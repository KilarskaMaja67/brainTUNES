"""
STEROWANIE NA ZYWO - opaska BrainAccess HALO. Zestaw kontrolek wybrany testami:
  - glowa w lewo / w prawo / w gore / w dol  (przechyl/odchyl i wroc)  -> akcelerometr
  - podwojne mrugniecie                                                 -> elektrody czolowe (EEG/EOG)

Zabezpieczenia:
  - pojedyncze (naturalne) mrugniecia sa ignorowane - liczy sie tylko 2x szybko,
  - regula mentorki: mrugniecie musi byc "wspolne" na Fp1 i Fp2 (Fp1-Fp2 male wzgledem sygnalu),
  - gdy glowa sie rusza, komendy z elektrod sa wstrzymane (ruch glowy robi zaklocenia w EEG).

KALIBRACJA prowadzi APLIKACJA (ekran Config): aplikacja wysyla CALIBRATE, Python mierzy i liczy,
a do aplikacji wysyla kroki (CAL:...), ocene kontaktu opaski i wynik. Progi zapisuja sie do kalibracja_final.json.
  - jest zapisana kalibracja -> Python wczytuje ja i od razu dziala,
  - nie ma -> Python czeka; aplikacja sama otwiera ekran kalibracji.

Komunikacja z aplikacja (UDP, tekst) - nazwy komend zmienisz w COMMANDS:
  Python -> apka (port PHONE_PORT):  LEFT, RIGHT, UP, DOWN, DOUBLE_BLINK, BLINK
                                     HI CALIBRATED / HI NOT_CALIBRATED   (odpowiedz na HELLO)
                                     CAL:<krok>, CAL:CONTACT GOOD|WEAK <szum>, CAL:RESULT BLINK=OK|WEAK HEAD=OK|WEAK,
                                     CAL:DONE / CAL:FAIL                 (podczas kalibracji)
  apka -> Python (port LISTEN_PORT): HELLO (co 2 s), CALIBRATE (kalibruj od nowa i nadpisz zapisane)

Stop: czerwony kwadrat w PyCharmie.  WAZNE: zamknij BrainAccess Board.
"""

import json
import os
import queue
import socket
import sys
import threading
import time
from pathlib import Path

import numpy as np
from scipy.signal import butter, sosfilt, sosfilt_zi

from brainaccess import core
from brainaccess.core.eeg_manager import EEGManager
import brainaccess.core.eeg_channel as eeg_channel
from brainaccess.core.gain_mode import GainMode

sys.stdout.reconfigure(line_buffering=True)

# ===================== USTAWIENIA =====================
DEVICE_NAME = None                # wpisz nazwe, zeby laczyc TYLKO z ta opaska
PREFERRED_DEVICE = "BA HALO 215"  # preferowana opaska; gdy jej nie ma - pierwsza znaleziona HALO
FRONTAL = [0, 1]                  # Fp1, Fp2 (1p, 2p)

CALIBRATE_ON_START = False        # True = kalibracja w konsoli przy kazdym starcie (bez aplikacji)
CONTACT_MAX_NOISE = 80            # uV: powyzej tego szumu na czole kontakt opaski uznajemy za slaby
CALIB_FILE = Path(__file__).with_name("kalibracja_final.json")

PHONE_IP =  None                   # None = zapamieta sie, gdy apka wysle HELLO (prawdziwy telefon)
                                  # "127.0.0.1" = EMULATOR w Android Studio (+ adb emu redir add udp:5005:5005)
DEVICE_NAME = "BA HALO 215"
PHONE_PORT = 5005
LISTEN_PORT = 5008

BLINK_MODE = "single"             # "single" = jedno MOCNE mrugniecie, "double" = podwojne mrugniecie
                                  # ("single" tylko dla osob, u ktorych celowe mrugniecie jest duzo silniejsze od naturalnego)

COMMANDS = {                      # co wysylamy do aplikacji
    "L": "LEFT", "P": "RIGHT", "GORA": "UP", "DOL": "DOWN", "BLINK2": "DOUBLE_BLINK", "BLINK1": "BLINK",
}

DOUBLE_WINDOW_S = 0.8             # domyslnie; kalibracja dopasowuje do tempa mrugania danej osoby
SINGLE_LOCK_S = 1.2               # tryb "single": po komendzie ignoruj elektrody przez tyle sekund
BLINK_GAP_S = 0.15                # min. odstep miedzy mrugnieciami
HEAD_COS_MIN = 0.6                # jak bardzo ruch musi pasowac do wzorca kierunku (0-1)
HEAD_REFRACTORY_S = 0.6           # min. odstep miedzy komendami glowy
GATE_AFTER_MOVE_S = 0.8           # tyle po ruchu glowy ignorujemy elektrody
SHOW_EVENTS = True                # wypisuj wykryte zdarzenia
# ======================================================

HEAD_DIRS = [("L", "GLOWA W LEWO (przechyl i wroc)"), ("P", "GLOWA W PRAWO (przechyl i wroc)"),
             ("GORA", "GLOWA W GORE (odchyl i wroc)"), ("DOL", "GLOWA W DOL (pochyl i wroc)")]


def beep() -> None:
    def _b():
        try:
            import winsound
            winsound.Beep(1200, 150)
        except Exception:
            pass
    threading.Thread(target=_b, daemon=True).start()


class Link:
    """UDP: komendy do aplikacji i polecenia od niej."""

    def __init__(self) -> None:
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("0.0.0.0", LISTEN_PORT))
        self.sock.setblocking(False)
        self.phone = (PHONE_IP, PHONE_PORT) if PHONE_IP else None
        self.calibrated = False          # odsylane aplikacji w odpowiedzi na HELLO

    def send(self, msg: str) -> None:
        if not msg.startswith("CAL:"):
            print(f">>> {msg}")
        if self.phone:
            try:
                self.sock.sendto(msg.encode(), self.phone)
            except OSError as e:
                print(f"(nie wyslano do telefonu: {e})")

    def poll(self) -> list:
        msgs = []
        while True:
            try:
                data, addr = self.sock.recvfrom(1024)
            except BlockingIOError:
                break
            except ConnectionResetError:
                continue
            if not PHONE_IP:                       # adres na sztywno (emulator) - nie nadpisujemy
                if self.phone is None or self.phone[0] != addr[0]:
                    print(f"(polaczono z aplikacja: {addr[0]})")
                self.phone = (addr[0], PHONE_PORT)
            m = data.decode(errors="ignore").strip().upper()
            if m == "HELLO":
                try:
                    status = "CALIBRATED" if self.calibrated else "NOT_CALIBRATED"
                    self.sock.sendto(f"HI {status}".encode(), self.phone)   # "jestem" + stan kalibracji
                except OSError:
                    pass
            msgs.append(m)
        return msgs


class Stream:
    """Odbiera dane z SDK i filtruje je na biezaco (filtry przyczynowe)."""

    def __init__(self) -> None:
        self.q: "queue.Queue[np.ndarray]" = queue.Queue()
        self.n = 0

    def on_chunk(self, chunk, size) -> None:
        self.q.put(np.array(chunk, dtype=float, copy=True))

    def setup(self, sr, rows, arows) -> None:
        self.sr, self.rows, self.arows = float(sr), rows, arows
        self.sos_e = butter(2, [0.5, 10], btype="bandpass", fs=sr, output="sos")
        self.sos_a = butter(2, 5, btype="lowpass", fs=sr, output="sos")
        self.zi_e = self.zi_a = None

    def read(self, timeout=0.5):
        try:
            c = self.q.get(timeout=timeout)
        except queue.Empty:
            return None
        fr = c[[self.rows[i] for i in FRONTAL], :]
        ac = c[self.arows, :]
        if self.zi_e is None:
            self.zi_e = sosfilt_zi(self.sos_e)[:, None, :] * fr[:, 0][None, :, None]
            self.zi_a = sosfilt_zi(self.sos_a)[:, None, :] * ac[:, 0][None, :, None]
        fe, self.zi_e = sosfilt(self.sos_e, fr, axis=1, zi=self.zi_e)
        fa, self.zi_a = sosfilt(self.sos_a, ac, axis=1, zi=self.zi_a)
        k = c.shape[1]
        t = (self.n + np.arange(k)) / self.sr
        self.n += k
        com = fe.mean(axis=0)            # wspolny sygnal z czola (mrugniecia)
        fpd = fe[0] - fe[1]              # Fp1 - Fp2 (przy mrugnieciu ~0)
        return t, com, fpd, fa

    def collect(self, seconds):
        need, got, parts = int(seconds * self.sr), 0, []
        t_end = time.time() + seconds + 5
        while got < need and time.time() < t_end:
            r = self.read()
            if r is not None:
                parts.append(r)
                got += len(r[0])
        if not parts:
            raise RuntimeError("Brak danych z opaski - sprawdz polaczenie.")
        return (np.concatenate([p[0] for p in parts]), np.concatenate([p[1] for p in parts]),
                np.concatenate([p[2] for p in parts]), np.concatenate([p[3] for p in parts], axis=1))


def find_peaks_simple(z, thr, sr):
    """Indeksy szczytow z > thr, oddalonych o co najmniej BLINK_GAP_S."""
    above = z > thr
    starts = np.flatnonzero(above[1:] & ~above[:-1]) + 1
    peaks, last = [], -10 ** 9
    for s0 in starts:
        e0 = s0
        while e0 < len(z) and above[e0]:
            e0 += 1
        p = s0 + int(np.argmax(z[s0:e0]))
        if p - last > BLINK_GAP_S * sr:
            peaks.append(p)
            last = p
    return peaks


def calibrate(st: Stream, link: Link) -> dict:
    sr = st.sr

    def say(text, code, sound=False):
        print(f"\n   >>> {text}")
        link.send(f"CAL:{code}")
        if sound:
            beep()

    print("\n=================== KALIBRACJA ===================")
    say("Siedz spokojnie, glowa prosto, patrz przed siebie (5 s)", "REST")
    st.collect(1.0)
    _, com_r, fpd_r, acc_r = st.collect(5.0)
    noise = float(np.percentile(np.abs(com_r), 95))
    g0 = np.median(acc_r, axis=1)
    acc_noise = float(np.percentile(np.linalg.norm(acc_r - g0[:, None], axis=0), 95))
    contact_ok = noise <= CONTACT_MAX_NOISE
    print(f"   Szum na czole: {noise:.0f} uV" + ("   (OK)" if contact_ok else "   (! duzy - popraw opaske na czole)"))
    link.send(f"CAL:CONTACT {'GOOD' if contact_ok else 'WEAK'} {noise:.0f}")

    # --- mrugniecia ---
    nb = 2 if BLINK_MODE == "double" else 1
    what = "PODWOJNE MRUGNIECIE (2x szybko)" if nb == 2 else "JEDNO MOCNE MRUGNIECIE"
    say(f"Za chwile: {what}. Po pisku wykonaj je wyraznie.", "GET_READY")
    st.collect(2.0)
    wins = []
    for i in range(5):
        say(f"MRUGNIJ {nb}x  ({i + 1}/5)", f"BLINK{nb} {i + 1}/5", True)
        wins.append(st.collect(3.5))        # dlugie okno: kazdy reaguje w swoim tempie
        say("...odpoczynek", "WAIT")
        st.collect(1.5)
    exts = [w[1][np.argmax(np.abs(w[1]))] for w in wins]
    pol = 1.0 if np.median(exts) >= 0 else -1.0
    tops, ratios, gaps = [], [], []
    for _, com, fpd, _ in wins:
        z = pol * com
        pk = find_peaks_simple(z, 0.4 * z.max(), sr)
        pk = sorted(sorted(pk, key=lambda p: -z[p])[:nb])
        tops += [float(z[p]) for p in pk]
        ratios += [float(abs(fpd[p]) / (abs(com[p]) + 1e-6)) for p in pk]
        if nb == 2 and len(pk) == 2:
            gaps.append((pk[1] - pk[0]) / sr)
    amp = float(np.median(tops))
    blink_thr = noise + 0.5 * (amp - noise)
    ratio_thr = float(np.clip(3 * np.median(ratios), 0.3, 1.0))
    double_win = float(np.clip(1.6 * np.median(gaps), 0.6, 2.0)) if gaps else DOUBLE_WINDOW_S
    counts = [len(find_peaks_simple(pol * w[1], blink_thr, sr)) for w in wins]
    margin = 2 if nb == 2 else 3  # pojedyncze musi byc wyraznie silniejsze od szumu
    if nb == 1:
        # pojedyncze: wystarczy, ze mocne mrugniecie jest w oknie (dodatkowe odruchowe mrugniecia nie psuja proby)
        blink_ok = amp > margin * noise and sum(c >= 1 for c in counts) >= 3
    else:
        blink_ok = amp > margin * noise and sum(c == 2 for c in counts) >= 3

    # --- ruchy glowy ---
    say("Za chwile: RUCHY GLOWY. Po pisku wykonaj ruch i od razu wroc do pozycji prostej.", "GET_READY")
    st.collect(2.0)
    peaks = {d: [] for d, _ in HEAD_DIRS}
    for d, txt in HEAD_DIRS:
        for i in range(3):
            say(f"{txt}  ({i + 1}/3)", f"HEAD_{d} {i + 1}/3", True)
            _, _, _, acc = st.collect(3.0)
            dev = acc - g0[:, None]
            k = int(np.argmax(np.linalg.norm(dev, axis=0)))
            peaks[d].append(dev[:, k])
            say("...glowa prosto", "WAIT")
            st.collect(1.5)
    templ = {d: np.mean([v / (np.linalg.norm(v) + 1e-9) for v in vs], axis=0) for d, vs in peaks.items()}
    templ = {d: v / (np.linalg.norm(v) + 1e-9) for d, v in templ.items()}
    amps = {d: float(np.median([np.linalg.norm(v) for v in vs])) for d, vs in peaks.items()}
    head_on = max(0.4 * min(amps.values()), 4 * acc_noise)
    correct = 0
    for d, vs in peaks.items():
        for v in vs:
            u = v / (np.linalg.norm(v) + 1e-9)
            best = max(templ, key=lambda k: u @ templ[k])
            correct += best == d
    max_cos = max(float(templ[a] @ templ[b]) for a in templ for b in templ if a < b)

    cfg = {
        "mode": BLINK_MODE, "pol": pol, "blink_thr": blink_thr, "ratio_thr": ratio_thr,
        "double_win": double_win, "blink_ok": bool(blink_ok),
        "g0": g0.tolist(), "head_on": head_on, "templ": {d: v.tolist() for d, v in templ.items()},
        "debug": {"szum_czolo": noise, "mrugniecie": amp, "liczby_szczytow": counts,
                  "amp_glowa": amps, "szum_acc": acc_noise, "max_cos_kierunkow": max_cos},
    }
    CALIB_FILE.write_text(json.dumps(cfg, indent=2))

    print("\n---------------- WYNIK KALIBRACJI ----------------")
    print(f"Mrugniecia ({BLINK_MODE}): sila ~{amp:.0f} uV, szum ~{noise:.0f} uV, prog {blink_thr:.0f} uV, "
          f"wykryte szczyty w probach: {counts}  [{'OK' if blink_ok else 'SLABO'}]")
    if nb == 2:
        print(f"   odstep miedzy mrugnieciami ~{np.median(gaps) if gaps else float('nan'):.2f} s "
              f"-> okno podwojnego {double_win:.2f} s")
    print(f"Glowa: sila ruchu {', '.join(f'{d} {a:.1f}' for d, a in amps.items())} | "
          f"prog {head_on:.2f} | rozpoznane {correct}/{3 * len(HEAD_DIRS)} | "
          f"podobienstwo kierunkow max {max_cos:.2f}")
    if not blink_ok:
        print("! Mrugniecie niepewne - popraw opaske na czole albo mrugaj wyrazniej i skalibruj ponownie.")
        if nb == 1:
            print("  (w trybie 'single' mrugniecie musi byc min. 5x silniejsze od szumu - inaczej uzyj BLINK_MODE = 'double')")
    if correct < 3 * len(HEAD_DIRS) - 1 or max_cos > 0.7:
        print("! Kierunki glowy slabo rozroznialne - rob wyrazniejsze ruchy.")
    head_ok = correct >= 3 * len(HEAD_DIRS) - 1 and max_cos <= 0.7
    link.send(f"CAL:RESULT BLINK={'OK' if blink_ok else 'WEAK'} HEAD={'OK' if head_ok else 'WEAK'}")
    link.send("CAL:DONE" if blink_ok and head_ok else "CAL:FAIL")
    link.calibrated = True
    print(f"Zapisano: {CALIB_FILE.name}")
    print("==================================================\n")
    return cfg


class Detector:
    def __init__(self, cfg, link, sr) -> None:
        self.c, self.link, self.sr = cfg, link, sr
        self.g0 = np.array(cfg["g0"])
        self.templ = {d: np.array(v) for d, v in cfg["templ"].items()}
        self.head_active = False
        self.head_emitted = False
        self.head_peak = None
        self.last_head_cmd = -10.0
        self.last_move = -10.0
        self.peaks = []
        self.above = False
        self.cur_max = 0.0
        self.cur_t = 0.0
        self.cur_ratio = 1.0
        self.blink_lock = -10.0

    def process(self, t, com, fpd, acc) -> None:
        c = self.c
        on = c["head_on"]
        for i in range(len(t)):
            ti = t[i]
            # ---------- glowa (akcelerometr) ----------
            d = acc[:, i] - self.g0
            nd = float(np.linalg.norm(d))
            if not self.head_active:
                if nd > on:
                    self.head_active, self.head_emitted, self.head_peak = True, False, d.copy()
                elif nd < 0.25 * on:
                    self.g0 += (acc[:, i] - self.g0) / (3 * self.sr)   # powolne sledzenie pozycji glowy
            if self.head_active:
                self.last_move = ti
                if nd > np.linalg.norm(self.head_peak):
                    self.head_peak = d.copy()
                pn = float(np.linalg.norm(self.head_peak))
                if not self.head_emitted and nd < 0.7 * pn:
                    u = self.head_peak / (pn + 1e-9)
                    best = max(self.templ, key=lambda k: u @ self.templ[k])
                    if u @ self.templ[best] >= HEAD_COS_MIN and ti - self.last_head_cmd > HEAD_REFRACTORY_S:
                        self.link.send(COMMANDS[best])
                        self.last_head_cmd = ti
                    elif SHOW_EVENTS:
                        print(f"   (ruch glowy nierozpoznany, zgodnosc {u @ self.templ[best]:.2f})")
                    self.head_emitted = True
                if nd < 0.3 * on:
                    self.head_active = False
            # ---------- podwojne mrugniecie (EEG/EOG) ----------
            gated = self.head_active or ti - self.last_move < GATE_AFTER_MOVE_S or nd > 0.5 * on
            if gated:
                self.peaks.clear()
                self.above = False
                continue
            z = c["pol"] * com[i]
            if z > c["blink_thr"]:
                if not self.above:
                    self.above, self.cur_max = True, -1.0
                if z > self.cur_max:
                    self.cur_max, self.cur_t = z, ti
                    self.cur_ratio = abs(fpd[i]) / (abs(com[i]) + 1e-6)
            elif self.above and z < 0.5 * c["blink_thr"]:
                self.above = False
                single = c.get("mode", "double") == "single"
                if single and self.cur_ratio <= c["ratio_thr"] and ti - self.blink_lock > SINGLE_LOCK_S:
                    self.link.send(COMMANDS["BLINK1"])
                    self.blink_lock = ti
                    continue
                if single:
                    continue
                if self.cur_ratio <= c["ratio_thr"] and ti - self.blink_lock > 0.5:
                    if not self.peaks or self.cur_t - self.peaks[-1] > BLINK_GAP_S:
                        self.peaks.append(self.cur_t)
                elif SHOW_EVENTS and self.cur_ratio > c["ratio_thr"]:
                    print(f"   (odrzucone: Fp1-Fp2 za duze - to nie mrugniecie, {self.cur_ratio:.2f})")
            self.peaks = [p for p in self.peaks if ti - p < c.get("double_win", DOUBLE_WINDOW_S)]
            if len(self.peaks) >= 2:
                self.link.send(COMMANDS["BLINK2"])
                self.peaks.clear()
                self.blink_lock = ti


def print_running(cfg) -> None:
    print(f"DZIALA! Glowa: lewo/prawo/gora/dol, mrugniecie "
          f"{'2x' if cfg.get('mode', 'double') == 'double' else '1x mocno'}.  (Stop = czerwony kwadrat)\n")


def main() -> None:
    link = Link()
    core.init()
    if DEVICE_NAME:
        name = DEVICE_NAME
    else:
        names = [d.name for d in core.scan()]
        print("Znalezione urzadzenia:", names)
        halo = [n for n in names if "HALO" in n.upper()]
        if not halo:
            raise SystemExit("Nie znaleziono opaski HALO. Czy jest wlaczona, a BrainAccess Board zamkniety?")
        name = PREFERRED_DEVICE if PREFERRED_DEVICE in names else halo[0]

    st = Stream()
    mgr = EEGManager()
    mgr.__enter__()
    try:
        print("Lacze z:", name)
        mgr.connect(name)
        print(f"Bateria: {mgr.get_battery_info().level} %")
        feats = mgr.get_device_features()
        if not feats.has_accel():
            raise SystemExit("Ta opaska nie ma akcelerometru - ten zestaw kontrolek wymaga go.")
        el = list(range(feats.electrode_count()))
        for e in el:
            mgr.set_channel_enabled(eeg_channel.ELECTRODE_MEASUREMENT + e, True)
            mgr.set_channel_gain(eeg_channel.ELECTRODE_MEASUREMENT + e, GainMode.X8)
        mgr.set_channel_bias_feedback(eeg_channel.ELECTRODE_MEASUREMENT + el[-1])
        acc_ch = [eeg_channel.ACCELEROMETER + a for a in range(3)]
        for ch in acc_ch:
            mgr.set_channel_enabled(ch, True)
        mgr.set_callback_chunk(st.on_chunk)
        mgr.load_config()
        sr = mgr.get_sample_frequency()
        st.setup(sr, [mgr.get_channel_index(eeg_channel.ELECTRODE_MEASUREMENT + e) for e in el],
                 [mgr.get_channel_index(ch) for ch in acc_ch])
        mgr.start_stream()

        print("Rozgrzewka 3 s...")
        st.collect(3.0)
        det = None
        if CALIBRATE_ON_START:
            cfg = calibrate(st, link)
            det = Detector(cfg, link, st.sr)
        elif CALIB_FILE.exists():
            cfg = json.loads(CALIB_FILE.read_text())
            print(f"Wczytano kalibracje: {CALIB_FILE.name}")
            _, _, _, acc = st.collect(1.0)
            cfg["g0"] = np.median(acc, axis=1).tolist()   # aktualna pozycja glowy
            link.calibrated = True
            det = Detector(cfg, link, st.sr)
        else:
            print("Brak zapisanej kalibracji - czekam, az aplikacja ja uruchomi (ekran kalibracji)...")
        if det:
            print_running(cfg)

        last_data = time.time()
        while True:
            if "CALIBRATE" in link.poll():
                cfg = calibrate(st, link)
                link.poll()                       # wyrzuc ewentualne powtorzone CALIBRATE z kolejki
                det = Detector(cfg, link, st.sr)
                print_running(cfg)
            r = st.read()
            if r is None:
                if time.time() - last_data > 2:
                    print("Brak danych z opaski... (sprawdz polaczenie)")
                    last_data = time.time()
                continue
            last_data = time.time()
            if det:                                   # bez kalibracji nic nie wykrywamy
                det.process(*r)
    except KeyboardInterrupt:
        print("\nZatrzymuje...")
    finally:
        def _close():
            try:
                mgr.stop_stream()
                mgr.__exit__(None, None, None)
                core.close()
            except Exception:
                pass
        th = threading.Thread(target=_close, daemon=True)
        th.start()
        th.join(timeout=5)
        print("Rozlaczono.")
        os._exit(0)


if __name__ == "__main__":
    main()