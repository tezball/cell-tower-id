package com.terrycollins.celltowerid.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.terrycollins.celltowerid.R
import com.terrycollins.celltowerid.databinding.ActivityTowerDetailBinding
import com.terrycollins.celltowerid.databinding.ItemHistoryBinding
import com.terrycollins.celltowerid.domain.model.AnomalySeverity
import com.terrycollins.celltowerid.domain.model.AnomalyType
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.ui.viewmodel.TowerDetailViewModel
import com.terrycollins.celltowerid.util.CellIdParser
import com.terrycollins.celltowerid.util.CellPropertyHelp
import com.terrycollins.celltowerid.util.SignalClassifier
import androidx.appcompat.app.AlertDialog
import com.terrycollins.celltowerid.BuildConfig
import com.terrycollins.celltowerid.util.UsCarriers
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TowerDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTowerDetailBinding
    private val viewModel: TowerDetailViewModel by viewModels()
    private var mapView: MapView? = null
    private var layersInitialized = false

    companion object {
        private const val SOURCE_YOU = "you-are-here"
        private const val LAYER_YOU = "you-are-here-layer"
        private const val SOURCE_TOWER = "tower-estimate"
        private const val LAYER_TOWER = "tower-estimate-layer"

        const val EXTRA_RADIO = "extra_radio"
        const val EXTRA_MCC = "extra_mcc"
        const val EXTRA_MNC = "extra_mnc"
        const val EXTRA_TAC_LAC = "extra_tac_lac"
        const val EXTRA_CID = "extra_cid"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        // Pass current measurement data as extras
        const val EXTRA_PCI = "extra_pci"
        const val EXTRA_EARFCN = "extra_earfcn"
        const val EXTRA_BAND = "extra_band"
        const val EXTRA_BANDWIDTH = "extra_bandwidth"
        const val EXTRA_RSRP = "extra_rsrp"
        const val EXTRA_RSRQ = "extra_rsrq"
        const val EXTRA_RSSI = "extra_rssi"
        const val EXTRA_SINR = "extra_sinr"
        const val EXTRA_CQI = "extra_cqi"
        const val EXTRA_TA = "extra_ta"
        const val EXTRA_SIGNAL_LEVEL = "extra_signal_level"
        const val EXTRA_IS_REGISTERED = "extra_is_registered"
        const val EXTRA_OPERATOR = "extra_operator"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_GPS_ACCURACY = "extra_gps_accuracy"
        const val EXTRA_ALERT_TYPE = "extra_alert_type"
        const val EXTRA_ALERT_SEVERITY = "extra_alert_severity"
        const val EXTRA_ALERT_DESCRIPTION = "extra_alert_description"
    }

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        binding = ActivityTowerDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val measurement = extractMeasurement() ?: run { finish(); return }

        populateAlertCard()
        populateHeader(measurement)
        populateIdentityTable(measurement)
        populateSignalTable(measurement)
        populateLocationTable(measurement, null, null, measurement.timingAdvance?.let {
            if (measurement.radio == RadioType.LTE && it > 0) it * 78.12 else null
        })
        setupMap(savedInstanceState, measurement)
        observeViewModel(measurement)
        viewModel.loadHistory(measurement)

        binding.buttonLocate.setOnClickListener {
            val intent = android.content.Intent(this, LocateActivity::class.java).apply {
                putExtra(LocateActivity.EXTRA_RADIO, measurement.radio.name)
                measurement.mcc?.let { putExtra(LocateActivity.EXTRA_MCC, it) }
                measurement.mnc?.let { putExtra(LocateActivity.EXTRA_MNC, it) }
                measurement.tacLac?.let { putExtra(LocateActivity.EXTRA_TAC_LAC, it) }
                measurement.cid?.let { putExtra(LocateActivity.EXTRA_CID, it) }
            }
            startActivity(intent)
        }
    }

    private fun extractMeasurement(): CellMeasurement? {
        val radioStr = intent.getStringExtra(EXTRA_RADIO) ?: return null
        return CellMeasurement(
            timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis()),
            latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0),
            longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0),
            gpsAccuracy = if (intent.hasExtra(EXTRA_GPS_ACCURACY)) intent.getFloatExtra(EXTRA_GPS_ACCURACY, 0f) else null,
            radio = RadioType.fromString(radioStr),
            mcc = intentIntOrNull(EXTRA_MCC),
            mnc = intentIntOrNull(EXTRA_MNC),
            tacLac = intentIntOrNull(EXTRA_TAC_LAC),
            cid = if (intent.hasExtra(EXTRA_CID)) intent.getLongExtra(EXTRA_CID, 0) else null,
            pciPsc = intentIntOrNull(EXTRA_PCI),
            earfcnArfcn = intentIntOrNull(EXTRA_EARFCN),
            bandwidth = intentIntOrNull(EXTRA_BANDWIDTH),
            band = intentIntOrNull(EXTRA_BAND),
            rsrp = intentIntOrNull(EXTRA_RSRP),
            rsrq = intentIntOrNull(EXTRA_RSRQ),
            rssi = intentIntOrNull(EXTRA_RSSI),
            sinr = intentIntOrNull(EXTRA_SINR),
            cqi = intentIntOrNull(EXTRA_CQI),
            timingAdvance = intentIntOrNull(EXTRA_TA),
            signalLevel = intentIntOrNull(EXTRA_SIGNAL_LEVEL),
            isRegistered = intent.getBooleanExtra(EXTRA_IS_REGISTERED, false),
            operatorName = intent.getStringExtra(EXTRA_OPERATOR)
        )
    }

    private fun intentIntOrNull(key: String): Int? =
        if (intent.hasExtra(key)) intent.getIntExtra(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null

    private fun populateAlertCard() {
        val typeName = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: return
        val severityName = intent.getStringExtra(EXTRA_ALERT_SEVERITY) ?: return
        val description = intent.getStringExtra(EXTRA_ALERT_DESCRIPTION) ?: return

        val type = runCatching { AnomalyType.valueOf(typeName) }.getOrNull() ?: return
        val severity = runCatching { AnomalySeverity.valueOf(severityName) }.getOrNull() ?: return

        binding.alertSectionHeader.visibility = View.VISIBLE
        binding.alertCard.visibility = View.VISIBLE

        val severityColor = Color.parseColor(severity.colorHex)
        binding.alertSeverityIndicator.setBackgroundColor(severityColor)
        binding.alertSeverityIndicator.contentDescription = severity.displayName

        binding.alertType.text = type.displayName

        binding.alertSeverityBadge.text = severity.displayName
        binding.alertSeverityBadge.background = GradientDrawable().apply {
            setColor(severityColor)
            cornerRadius = 12f
        }

        binding.alertDescription.text = description
        binding.alertExplanation.text = type.explanation

        type.drivingNote?.let { note ->
            binding.alertDrivingNote.visibility = View.VISIBLE
            binding.alertDrivingNote.text = "While driving: $note"
        }
    }

    private fun populateHeader(m: CellMeasurement) {
        val quality = SignalClassifier.classify(m)

        // Signal value
        val signalDb = m.rsrp ?: m.rssi
        binding.textSignalValue.text = if (signalDb != null) "$signalDb" else "?"
        binding.textSignalValue.setTextColor(Color.parseColor(quality.colorHex))

        // Quality label
        binding.textSignalQuality.text = quality.label
        binding.textSignalQuality.setTextColor(Color.parseColor(quality.colorHex))

        // Radio + carrier
        val carrier = if (m.mcc != null && m.mnc != null) {
            UsCarriers.getCarrierName(m.mcc, m.mnc) ?: "${m.mcc}/${m.mnc}"
        } else "Unknown"
        binding.textRadioCarrier.text = "${m.radio.name} | $carrier"

        // Serving badge
        binding.textServingBadge.visibility = if (m.isRegistered) View.VISIBLE else View.GONE

        // Toolbar title
        val cid = m.cid
        val titleSuffix = if (cid != null && m.radio == RadioType.LTE) {
            val (enb, sector) = CellIdParser.parseEutranCid(cid)
            "eNB $enb / Sector $sector"
        } else if (cid != null) {
            "CID $cid"
        } else {
            m.radio.name
        }
        binding.toolbar.title = "$carrier - $titleSuffix"
    }

    private fun populateIdentityTable(m: CellMeasurement) {
        val table = binding.tableIdentity
        addRow(table, "Technology", m.radio.name, helpKey = CellPropertyHelp.Key.TECHNOLOGY)
        m.mcc?.let { addRow(table, "MCC", it.toString(), helpKey = CellPropertyHelp.Key.MCC) }
        m.mnc?.let { addRow(table, "MNC", it.toString(), helpKey = CellPropertyHelp.Key.MNC) }
        m.tacLac?.let {
            val isLte = m.radio == RadioType.LTE || m.radio == RadioType.NR
            val label = if (isLte) "TAC" else "LAC"
            val key = if (isLte) CellPropertyHelp.Key.TAC else CellPropertyHelp.Key.LAC
            addRow(table, label, it.toString(), helpKey = key)
        }
        m.cid?.let { cid ->
            when (m.radio) {
                RadioType.LTE -> {
                    addRow(table, "E-UTRAN CID", cid.toString(), helpKey = CellPropertyHelp.Key.E_UTRAN_CID)
                    val (enb, sector) = CellIdParser.parseEutranCid(cid)
                    addRow(table, "eNodeB ID", enb.toString(), helpKey = CellPropertyHelp.Key.ENODEB_ID)
                    addRow(table, "Sector ID", sector.toString(), helpKey = CellPropertyHelp.Key.SECTOR_ID)
                }
                RadioType.NR -> {
                    addRow(table, "NR Cell ID (NCI)", cid.toString(), helpKey = CellPropertyHelp.Key.NR_NCI)
                }
                else -> {
                    addRow(table, "Cell ID", cid.toString(), helpKey = CellPropertyHelp.Key.CID_GENERIC)
                }
            }
        }
        m.pciPsc?.let {
            val (label, key) = when (m.radio) {
                RadioType.LTE, RadioType.NR -> "PCI" to CellPropertyHelp.Key.PCI
                RadioType.WCDMA -> "PSC" to CellPropertyHelp.Key.PSC
                RadioType.GSM -> "BSIC" to CellPropertyHelp.Key.BSIC
                else -> "PCI/PSC" to CellPropertyHelp.Key.PCI
            }
            addRow(table, label, it.toString(), helpKey = key)
        }
        m.earfcnArfcn?.let {
            val (label, key) = when (m.radio) {
                RadioType.LTE -> "EARFCN" to CellPropertyHelp.Key.EARFCN
                RadioType.NR -> "NRARFCN" to CellPropertyHelp.Key.NRARFCN
                RadioType.WCDMA -> "UARFCN" to CellPropertyHelp.Key.UARFCN
                RadioType.GSM -> "ARFCN" to CellPropertyHelp.Key.ARFCN
                else -> "ARFCN" to CellPropertyHelp.Key.ARFCN
            }
            addRow(table, label, it.toString(), helpKey = key)
        }
        m.band?.let { addRow(table, "Band", it.toString(), helpKey = CellPropertyHelp.Key.BAND) }
        m.bandwidth?.let { addRow(table, "Bandwidth", "${it / 1000} MHz", helpKey = CellPropertyHelp.Key.BANDWIDTH) }
        m.operatorName?.let { addRow(table, "Operator", it, helpKey = CellPropertyHelp.Key.OPERATOR) }
        if (m.mcc != null && m.mnc != null) {
            UsCarriers.getCarrierName(m.mcc, m.mnc)?.let {
                addRow(table, "Carrier", it, helpKey = CellPropertyHelp.Key.CARRIER)
            }
        }
        addRow(
            table,
            "Registered",
            if (m.isRegistered) "Yes (Serving)" else "No (Neighbor)",
            helpKey = CellPropertyHelp.Key.REGISTERED
        )
    }

    private fun populateSignalTable(m: CellMeasurement) {
        val table = binding.tableSignal
        val quality = SignalClassifier.classify(m)
        addRow(
            table, "Signal Quality", quality.label,
            Color.parseColor(quality.colorHex),
            helpKey = CellPropertyHelp.Key.SIGNAL_QUALITY
        )
        m.signalLevel?.let {
            addRow(table, "Signal Level", "$it / 4 bars", helpKey = CellPropertyHelp.Key.SIGNAL_LEVEL)
        }
        m.rsrp?.let {
            addRowWithQuality(
                table, "RSRP", "$it dBm",
                SignalClassifier.classifyLteRsrp(it),
                helpKey = CellPropertyHelp.Key.RSRP
            )
        }
        m.rsrq?.let { addRow(table, "RSRQ", "$it dB", helpKey = CellPropertyHelp.Key.RSRQ) }
        m.rssi?.let { addRow(table, "RSSI", "$it dBm", helpKey = CellPropertyHelp.Key.RSSI) }
        m.sinr?.let {
            addRowWithQuality(
                table, "SINR", "$it dB",
                SignalClassifier.classifyLteSinr(it),
                helpKey = CellPropertyHelp.Key.SINR
            )
        }
        m.cqi?.let { addRow(table, "CQI", it.toString(), helpKey = CellPropertyHelp.Key.CQI) }
        m.timingAdvance?.let {
            addRow(table, "Timing Advance", it.toString(), helpKey = CellPropertyHelp.Key.TIMING_ADVANCE)
            if (m.radio == RadioType.LTE) {
                val distMeters = it * 78.12
                addRow(table, "Est. Distance", "~${distMeters.toInt()} m",
                    helpKey = CellPropertyHelp.Key.EST_DISTANCE)
            }
        }
    }

    private fun populateLocationTable(m: CellMeasurement, towerLat: Double?, towerLon: Double?, estimatedDistanceM: Double?) {
        val table = binding.tableLocation

        if (towerLat != null && towerLon != null) {
            addRow(table, "Tower Location", "Estimated from measurements",
                helpKey = CellPropertyHelp.Key.TOWER_LOCATION)
            addRow(table, "Tower Latitude", "%.6f".format(towerLat),
                helpKey = CellPropertyHelp.Key.TOWER_LAT)
            addRow(table, "Tower Longitude", "%.6f".format(towerLon),
                helpKey = CellPropertyHelp.Key.TOWER_LON)
        }

        estimatedDistanceM?.let {
            addRow(table, "Est. Distance", "~${it.toInt()} m",
                helpKey = CellPropertyHelp.Key.EST_DISTANCE)
        }

        addRow(table, "Your Latitude", "%.6f".format(m.latitude),
            helpKey = CellPropertyHelp.Key.YOUR_LAT)
        addRow(table, "Your Longitude", "%.6f".format(m.longitude),
            helpKey = CellPropertyHelp.Key.YOUR_LON)
        m.gpsAccuracy?.let {
            addRow(table, "GPS Accuracy", "%.1f m".format(it),
                helpKey = CellPropertyHelp.Key.GPS_ACCURACY)
        }
        addRow(table, "Observed At", dateFormat.format(Date(m.timestamp)),
            helpKey = CellPropertyHelp.Key.OBSERVED_AT)
    }

    private fun setupMap(savedInstanceState: Bundle?, m: CellMeasurement) {
        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { map ->
            map.setStyle(BuildConfig.TILE_STYLE_URL) { style ->
                initLayers(style)
                val pos = LatLng(m.latitude, m.longitude)
                updateYouSource(style, pos)
                map.cameraPosition = CameraPosition.Builder()
                    .target(pos)
                    .zoom(15.0)
                    .build()
            }
        }
    }

    private fun initLayers(style: Style) {
        if (layersInitialized) return
        style.addSource(GeoJsonSource(SOURCE_YOU, FeatureCollection.fromFeatures(emptyArray())))
        val youLayer = CircleLayer(LAYER_YOU, SOURCE_YOU).apply {
            setProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(Color.parseColor("#2962FF")),
                PropertyFactory.circleOpacity(0.9f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        }
        style.addLayer(youLayer)

        style.addSource(GeoJsonSource(SOURCE_TOWER, FeatureCollection.fromFeatures(emptyArray())))
        val towerLayer = CircleLayer(LAYER_TOWER, SOURCE_TOWER).apply {
            setProperties(
                PropertyFactory.circleRadius(12f),
                PropertyFactory.circleColor(Color.parseColor("#6200EA")),
                PropertyFactory.circleOpacity(0.85f),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        }
        style.addLayer(towerLayer)
        layersInitialized = true
    }

    private fun updateYouSource(style: Style, pos: LatLng) {
        val feature = Feature.fromGeometry(Point.fromLngLat(pos.longitude, pos.latitude))
        style.getSourceAs<GeoJsonSource>(SOURCE_YOU)
            ?.setGeoJson(FeatureCollection.fromFeatures(arrayOf(feature)))
    }

    private fun updateTowerSource(style: Style, pos: LatLng?) {
        val features = if (pos == null) emptyArray() else arrayOf(
            Feature.fromGeometry(Point.fromLngLat(pos.longitude, pos.latitude))
        )
        style.getSourceAs<GeoJsonSource>(SOURCE_TOWER)
            ?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun observeViewModel(current: CellMeasurement) {
        viewModel.state.observe(this) { state ->
            binding.tableLocation.removeAllViews()
            populateLocationTable(current, state.towerLat, state.towerLon, state.distanceMeters)

            binding.textHistoryCount.text = "${state.history.size} measurements recorded for this tower"

            if (state.history.isNotEmpty()) {
                binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
                binding.recyclerHistory.adapter = HistoryAdapter(state.history)
            }

            updateMapWithTowerInfo(current, state.towerLat, state.towerLon, state.distanceMeters, state.allPoints)
        }
    }

    private fun updateMapWithTowerInfo(
        current: CellMeasurement,
        towerLat: Double?,
        towerLon: Double?,
        distanceM: Double?,
        allPoints: List<CellMeasurement>
    ) {
        mapView?.getMapAsync { map ->
            val style = map.style ?: return@getMapAsync
            initLayers(style)

            // Update "you are here" source
            val yourPos = LatLng(current.latitude, current.longitude)
            updateYouSource(style, yourPos)

            if (towerLat != null && towerLon != null) {
                // We have a tower location estimate -- show it
                val towerPos = LatLng(towerLat, towerLon)
                updateTowerSource(style, towerPos)

                // Zoom to fit both markers
                val bounds = LatLngBounds.Builder()
                    .include(yourPos)
                    .include(towerPos)
                    .build()
                map.cameraPosition = CameraPosition.Builder()
                    .target(bounds.center)
                    .zoom(14.0)
                    .build()
            } else {
                // No tower location -- center on your position
                updateTowerSource(style, null)
                map.cameraPosition = CameraPosition.Builder()
                    .target(yourPos)
                    .zoom(15.0)
                    .build()
            }

            // Show all measurement points as a spread
            if (allPoints.size > 1) {
                val features = allPoints.map { m ->
                    Feature.fromGeometry(
                        Point.fromLngLat(m.longitude, m.latitude)
                    ).apply {
                        addNumberProperty("rsrp", (m.rsrp ?: -120).toDouble())
                    }
                }
                val fc = FeatureCollection.fromFeatures(features)

                val existingSource = style.getSourceAs<GeoJsonSource>("history")
                if (existingSource != null) {
                    existingSource.setGeoJson(fc)
                } else {
                    style.addSource(GeoJsonSource("history", fc))

                    val layer = CircleLayer("history-points", "history")
                    layer.setProperties(
                        PropertyFactory.circleRadius(5f),
                        PropertyFactory.circleColor(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.get("rsrp"),
                                Expression.stop(-120, Expression.color(Color.parseColor("#D50000"))),
                                Expression.stop(-100, Expression.color(Color.parseColor("#FF6D00"))),
                                Expression.stop(-90, Expression.color(Color.parseColor("#FFD600"))),
                                Expression.stop(-80, Expression.color(Color.parseColor("#00C853")))
                            )
                        ),
                        PropertyFactory.circleOpacity(0.7f),
                        PropertyFactory.circleStrokeWidth(1f),
                        PropertyFactory.circleStrokeColor(Color.WHITE)
                    )
                    style.addLayer(layer)
                }
            }
        }
    }

    // --- Table helpers ---

    private fun resolveThemeColor(attrResId: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attrResId, typedValue, true)
        return typedValue.data
    }

    private fun addRow(
        table: TableLayout,
        label: String,
        value: String,
        valueColor: Int? = null,
        helpKey: CellPropertyHelp.Key? = null
    ) {
        val row = TableRow(this).apply {
            setPadding(0, 4, 0, 4)
        }
        val labelText = if (helpKey != null) "$label  ⓘ" else label
        val labelColor = if (helpKey != null) {
            resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
        } else {
            resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        }
        row.addView(TextView(this).apply {
            text = labelText
            textSize = 13f
            setTextColor(labelColor)
            setPadding(0, 0, 24, 0)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(valueColor ?: resolveThemeColor(com.google.android.material.R.attr.colorOnSurface))
            if (valueColor != null) setTypeface(null, android.graphics.Typeface.BOLD)
        })
        row.contentDescription = "$label: $value"
        if (helpKey != null) {
            row.isClickable = true
            row.isFocusable = true
            row.contentDescription = "$label: $value. Tap for help."
            row.setOnClickListener { showHelp(helpKey) }
        }
        table.addView(row)
    }

    private fun addRowWithQuality(
        table: TableLayout,
        label: String,
        value: String,
        quality: com.terrycollins.celltowerid.domain.model.SignalQuality,
        helpKey: CellPropertyHelp.Key? = null
    ) {
        addRow(table, label, "$value  (${quality.label})", Color.parseColor(quality.colorHex), helpKey)
    }

    private fun showHelp(key: CellPropertyHelp.Key) {
        val help = CellPropertyHelp.get(key) ?: return
        AlertDialog.Builder(this)
            .setTitle(help.title)
            .setMessage(help.body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // --- History adapter ---

    private inner class HistoryAdapter(
        private val items: List<CellMeasurement>
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        inner class VH(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            val quality = SignalClassifier.classify(m)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(quality.colorHex))
            }
            holder.binding.dot.background = bg
            holder.binding.dot.contentDescription = quality.label
            holder.binding.textTime.text = timeFormat.format(Date(m.timestamp))
            holder.binding.textRsrp.text = m.rsrp?.let { "${it} dBm" } ?: m.rssi?.let { "${it} dBm" } ?: "?"
            holder.binding.textRsrp.setTextColor(Color.parseColor(quality.colorHex))
            holder.binding.textSinr.text = m.sinr?.let { "SINR: ${it}dB" } ?: ""
        }

        override fun getItemCount() = items.size
    }

    // --- MapView lifecycle ---
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroy() { mapView?.onDestroy(); super.onDestroy() }
}
