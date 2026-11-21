package com.example.hardalarm

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class GoalsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        setContentView(R.layout.activity_goals)

        val prefs = getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
        val etDay = findViewById<EditText>(R.id.etDay)
        val etWeek = findViewById<EditText>(R.id.etWeek)
        val etMonth = findViewById<EditText>(R.id.etMonth)
        val etYear = findViewById<EditText>(R.id.etYear)

        etDay.setText(prefs.getString("goal_day", ""))
        etWeek.setText(prefs.getString("goal_week", ""))
        etMonth.setText(prefs.getString("goal_month", ""))
        etYear.setText(prefs.getString("goal_year", ""))

        findViewById<Button>(R.id.btnSaveGoals).setOnClickListener {
            prefs.edit()
                .putString("goal_day", etDay.text.toString())
                .putString("goal_week", etWeek.text.toString())
                .putString("goal_month", etMonth.text.toString())
                .putString("goal_year", etYear.text.toString())
                .apply()
            finish()
        }
    }
}
