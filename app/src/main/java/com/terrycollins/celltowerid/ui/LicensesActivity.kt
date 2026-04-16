package com.terrycollins.celltowerid.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.terrycollins.celltowerid.R
import com.terrycollins.celltowerid.util.AppLog
import com.google.android.material.appbar.MaterialToolbar

class LicensesActivity : AppCompatActivity() {

    private data class License(val name: String, val license: String, val url: String)

    private val licenses = listOf(
        License("MapLibre Native", "BSD 2-Clause", "https://github.com/maplibre/maplibre-native"),
        License(
            "OpenStreetMap data",
            "ODbL 1.0 - (c) OpenStreetMap contributors",
            "https://www.openstreetmap.org/copyright"
        ),
        License("OpenCellID", "CC BY-SA 4.0", "https://www.opencellid.org/"),
        License("OpenFreeMap tiles", "Open source", "https://openfreemap.org/"),
        License(
            "Material Components",
            "Apache 2.0",
            "https://github.com/material-components/material-components-android"
        ),
        License(
            "AndroidX (Room, Lifecycle, ConstraintLayout)",
            "Apache 2.0",
            "https://developer.android.com/jetpack/androidx"
        ),
        License(
            "Kotlin Coroutines",
            "Apache 2.0",
            "https://github.com/Kotlin/kotlinx.coroutines"
        ),
        License("Gson", "Apache 2.0", "https://github.com/google/gson"),
        License(
            "Google Play Services Location",
            "Google Play Services TOS",
            "https://developers.google.com/android/guides/overview"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_licenses)

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recycler_licenses)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = Adapter()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.text_license_name)
            val terms: TextView = view.findViewById(R.id.text_license_terms)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_license, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = licenses[position]
            holder.name.text = item.name
            holder.terms.text = item.license
            holder.itemView.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                } catch (e: Exception) {
                    AppLog.e("LicensesActivity", "failed to open ${item.url}", e)
                }
            }
        }

        override fun getItemCount(): Int = licenses.size
    }
}
