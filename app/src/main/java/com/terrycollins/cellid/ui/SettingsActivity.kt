package com.terrycollins.cellid.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This activity is declared in manifest but we use the fragment within MainActivity's nav instead.
        // If opened directly, just finish and go to main.
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
