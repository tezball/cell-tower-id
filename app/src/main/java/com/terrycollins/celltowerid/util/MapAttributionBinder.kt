package com.terrycollins.celltowerid.util

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.TextView

object MapAttributionBinder {

    const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

    fun bind(textView: TextView) {
        textView.isClickable = true
        textView.isFocusable = true
        textView.contentDescription =
            "Map attribution. Tap to view OpenStreetMap copyright."
        textView.setOnClickListener { openCopyrightPage(it) }
    }

    private fun openCopyrightPage(anchor: View) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(OSM_COPYRIGHT_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            anchor.context.startActivity(intent)
        } catch (e: Exception) {
            AppLog.e("MapAttributionBinder", "failed to open $OSM_COPYRIGHT_URL", e)
        }
    }
}
