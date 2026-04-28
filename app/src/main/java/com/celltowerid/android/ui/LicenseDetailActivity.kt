package com.celltowerid.android.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.celltowerid.android.R
import java.io.BufferedReader

class LicenseDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_RAW_RES_ID = "extra_raw_res_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_detail)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val rawResId = intent.getIntExtra(EXTRA_RAW_RES_ID, 0)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            this.title = title
            setNavigationOnClickListener { finish() }
        }

        val body = if (rawResId != 0) loadRaw(rawResId) else ""
        findViewById<TextView>(R.id.text_license_body).text = body
    }

    private fun loadRaw(rawResId: Int): String =
        resources.openRawResource(rawResId).bufferedReader().use(BufferedReader::readText)
}
