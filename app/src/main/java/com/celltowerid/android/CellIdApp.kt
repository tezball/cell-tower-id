package com.celltowerid.android

import android.app.Application
import com.celltowerid.android.util.AppLog
import com.celltowerid.android.util.CrashReporter
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil

class CellIdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        CrashReporter.install(this)
        AppLog.d("CellIdApp", "process start, log file=${AppLog.logFile(this).absolutePath}")
        installMapLibreHttpClient()
    }

    private fun installMapLibreHttpClient() {
        // OpenFreeMap and the OSM tile-usage policy ask client apps to
        // identify themselves on every tile request so operators can
        // contact the developer if usage misbehaves. Wrap MapLibre's
        // OkHttpClient with a UA interceptor.
        val ua = "CellTowerID/${BuildConfig.VERSION_NAME} " +
            "(${BuildConfig.APPLICATION_ID}; +https://cell-tower-id.com/)"
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .build()
                chain.proceed(req)
            }
            .build()
        HttpRequestUtil.setOkHttpClient(client)
    }
}
