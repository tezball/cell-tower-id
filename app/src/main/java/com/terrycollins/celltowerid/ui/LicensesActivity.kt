package com.terrycollins.celltowerid.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.terrycollins.celltowerid.R

class LicensesActivity : AppCompatActivity() {

    private data class License(
        val name: String,
        val terms: String,
        val rawResId: Int
    )

    private val licenses = listOf(
        License(
            "MapLibre Native",
            "BSD 2-Clause License — © Mapbox Inc. and MapLibre contributors",
            R.raw.license_bsd_2_clause_maplibre
        ),
        License(
            "OpenStreetMap data",
            "© OpenStreetMap contributors — Open Database License (ODbL) v1.0",
            R.raw.license_openstreetmap
        ),
        License(
            "OpenFreeMap (vector tiles)",
            "Free public tile service rendered from OpenStreetMap data",
            R.raw.license_openfreemap
        ),
        License(
            "Material Components for Android",
            "Apache License, Version 2.0",
            R.raw.license_apache_2_0
        ),
        License(
            "AndroidX (Core, AppCompat, Fragment, ConstraintLayout, RecyclerView, Preference, ViewPager2, Navigation, Room, Lifecycle, WorkManager)",
            "Apache License, Version 2.0",
            R.raw.license_apache_2_0
        ),
        License(
            "Kotlin Coroutines",
            "Apache License, Version 2.0",
            R.raw.license_apache_2_0
        ),
        License(
            "Gson",
            "Apache License, Version 2.0",
            R.raw.license_apache_2_0
        ),
        License(
            "OkHttp",
            "Apache License, Version 2.0 — © Square, Inc.",
            R.raw.license_apache_2_0
        ),
        License(
            "Google Play Services Location",
            "Google Play Services Terms of Service",
            R.raw.license_play_services
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
            holder.terms.text = item.terms
            holder.itemView.setOnClickListener {
                val intent = Intent(this@LicensesActivity, LicenseDetailActivity::class.java).apply {
                    putExtra(LicenseDetailActivity.EXTRA_TITLE, item.name)
                    putExtra(LicenseDetailActivity.EXTRA_RAW_RES_ID, item.rawResId)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int = licenses.size
    }
}
