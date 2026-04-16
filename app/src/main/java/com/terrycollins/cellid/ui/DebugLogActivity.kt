package com.terrycollins.cellid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.terrycollins.cellid.R
import com.terrycollins.cellid.util.AppLog
import com.terrycollins.cellid.util.LogLine
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugLogActivity : AppCompatActivity() {

    private val adapter = LogAdapter()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_log)

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recycler_log)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<MaterialButton>(R.id.btn_copy).setOnClickListener {
            val text = AppLog.lines().joinToString("\n") { formatLine(it) }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Cell Tower ID debug log", text))
        }
        findViewById<MaterialButton>(R.id.btn_refresh).setOnClickListener { refresh() }
        findViewById<MaterialButton>(R.id.btn_clear).setOnClickListener {
            AppLog.clear()
            refresh()
        }

        refresh()
    }

    private fun refresh() {
        adapter.submit(AppLog.lines())
    }

    private fun formatLine(l: LogLine): String =
        "${fmt.format(Date(l.ts))} ${l.level}/${l.tag}: ${l.message}"

    private inner class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

        private var items: List<LogLine> = emptyList()

        fun submit(list: List<LogLine>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(val textView: TextView) : RecyclerView.ViewHolder(textView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_debug_log, parent, false) as TextView
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.textView.text = formatLine(items[position])
        }

        override fun getItemCount(): Int = items.size
    }
}
