// sa 4 przyciski akordow, kazdym z nich gra sie innym ruchem glowy:
//   glowa w lewo = C, glowa w prawo = G, glowa w gore = a-moll, glowa w dol = F
//   mocne mrugniecie = powrot

package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class PlayActivity : AppCompatActivity() {

    private lateinit var CdurBtn: Button
    private lateinit var GdurBtn: Button
    private lateinit var AmolBtn: Button
    private lateinit var FdurBtn: Button

    private lateinit var akordy: Akordy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)

        akordy = Akordy(this)

        CdurBtn = findViewById(R.id.CdurBtn)
        GdurBtn = findViewById(R.id.GdurBtn)
        AmolBtn = findViewById(R.id.AmolBtn)
        FdurBtn = findViewById(R.id.FdurBtn)
        val backBtn = findViewById<Button>(R.id.backBtn)

        CdurBtn.setOnClickListener {
            CdurBtn.animujKlik()
            akordy.graj(Akordy.Akord.C)
        }

        GdurBtn.setOnClickListener {
            GdurBtn.animujKlik()
            akordy.graj(Akordy.Akord.G)
        }

        AmolBtn.setOnClickListener {
            AmolBtn.animujKlik()
            akordy.graj(Akordy.Akord.AM)
        }

        FdurBtn.setOnClickListener {
            FdurBtn.animujKlik()
            akordy.graj(Akordy.Akord.F)
        }

        backBtn.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        AppControler.ustawAkcje(
            lewo = { CdurBtn.performClick() },    // C
            prawo = { GdurBtn.performClick() },   // G
            gora = { AmolBtn.performClick() },    // a-moll
            dol = { FdurBtn.performClick() },     // F
            mrugniecie = { finish() },            // mocne mrugniecie = powrot do menu
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        akordy.zwolnij()
    }
}