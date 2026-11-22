package com.example.hardalarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class GoalsActivity : AppCompatActivity() {

    private var ivNotePreview: ImageView? = null
    private var tvNoPhoto: TextView? = null

    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            getSharedPreferences("hard_alarm", Context.MODE_PRIVATE)
                .edit()
                .putString("note_photo_uri", uri.toString())
                .apply()
            updatePhotoPreview(uri.toString())
        }
    }

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

        val previewId = resources.getIdentifier("ivNotePreview", "id", packageName)
        if (previewId != 0) ivNotePreview = findViewById(previewId)
        val noPhotoId = resources.getIdentifier("tvNoPhoto", "id", packageName)
        if (noPhotoId != 0) tvNoPhoto = findViewById(noPhotoId)

        val etNoteId = resources.getIdentifier("etAlarmNote", "id", packageName)
        val etAlarmNote = if (etNoteId != 0) findViewById<EditText>(etNoteId) else null
        etAlarmNote?.setText(prefs.getString("note_text", ""))

        updatePhotoPreview(prefs.getString("note_photo_uri", null))

        val changePhotoId = resources.getIdentifier("btnChangeNotePhoto", "id", packageName)
        if (changePhotoId != 0) {
            findViewById<Button>(changePhotoId)?.setOnClickListener {
                pickPhotoLauncher.launch(arrayOf("image/*"))
            }
        }

        val clearPhotoId = resources.getIdentifier("btnClearNotePhoto", "id", packageName)
        if (clearPhotoId != 0) {
            findViewById<Button>(clearPhotoId)?.setOnClickListener {
                prefs.edit().remove("note_photo_uri").apply()
                updatePhotoPreview(null)
            }
        }

        etDay.setText(prefs.getString("goal_day", ""))
        etWeek.setText(prefs.getString("goal_week", ""))
        etMonth.setText(prefs.getString("goal_month", ""))
        etYear.setText(prefs.getString("goal_year", ""))

        findViewById<Button>(R.id.btnSaveGoals).setOnClickListener {
            val editor = prefs.edit()
                .putString("goal_day", etDay.text.toString())
                .putString("goal_week", etWeek.text.toString())
                .putString("goal_month", etMonth.text.toString())
                .putString("goal_year", etYear.text.toString())
            if (etAlarmNote != null) {
                editor.putString("note_text", etAlarmNote.text.toString())
            }
            editor.apply()
            finish()
        }
    }

    private fun updatePhotoPreview(uriStr: String?) {
        if (!uriStr.isNullOrBlank()) {
            val loaded = runCatching {
                ivNotePreview?.setImageURI(Uri.parse(uriStr))
                true
            }.getOrDefault(false)
            ivNotePreview?.visibility = if (loaded) View.VISIBLE else View.GONE
            tvNoPhoto?.visibility = if (loaded) View.GONE else View.VISIBLE
        } else {
            ivNotePreview?.visibility = View.GONE
            tvNoPhoto?.visibility = View.VISIBLE
        }
    }
}
