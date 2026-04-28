package com.celltowerid.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.celltowerid.android.R
import com.celltowerid.android.util.AppLog
import com.celltowerid.android.util.CrashReporter
import com.celltowerid.android.util.LogLine
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
        findViewById<MaterialButton>(R.id.btn_show_crashes).setOnClickListener {
            showCrashes()
        }
        findViewById<MaterialButton>(R.id.btn_export_log).setOnClickListener {
            exportLog()
        }

        refresh()
    }

    private fun exportLog() {
        val file = AppLog.logFile(this)
        if (!file.exists() || file.length() == 0L) {
            Snackbar.make(findViewById(android.R.id.content), "Log file is empty", Snackbar.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Cell Tower ID log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Share log"))
    }

    private fun refresh() {
        adapter.submit(AppLog.lines())
    }

    private fun showCrashes() {
        val reports = CrashReporter.readAll(CrashReporter.crashDir(this))
        if (reports.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Crashes")
                .setMessage("No saved crash reports.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val body = reports.joinToString("\n\n---\n\n") { it.body }
        MaterialAlertDialogBuilder(this)
            .setTitle("Crashes (${reports.size})")
            .setMessage(body)
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Cell Tower ID crashes", body))
            }
            .setNeutralButton("Clear") { _, _ ->
                CrashReporter.clearAll(CrashReporter.crashDir(this))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
