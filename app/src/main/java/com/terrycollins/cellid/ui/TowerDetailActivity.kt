package com.terrycollins.cellid.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.terrycollins.cellid.R
import com.terrycollins.cellid.data.AppDatabase
import com.terrycollins.cellid.data.mapper.EntityMapper
import com.terrycollins.cellid.databinding.ActivityTowerDetailBinding
import com.terrycollins.cellid.databinding.ItemHistoryBinding
import com.terrycollins.cellid.domain.model.CellMeasurement
import com.terrycollins.cellid.domain.model.CellTower
import com.terrycollins.cellid.domain.model.RadioType
import com.terrycollins.cellid.repository.TowerCacheRepository
import com.terrycollins.cellid.util.CellIdParser
import com.terrycollins.cellid.util.CellPropertyHelp
import com.terrycollins.cellid.util.SignalClassifier
import com.terrycollins.cellid.util.TowerLocator
import androidx.appcompat.app.AlertDialog
import com.terrycollins.cellid.BuildConfig
import com.terrycollins.cellid.util.UsCarriers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
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
    }

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        binding = ActivityTowerDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val measurement = extractMeasurement() ?: run { finish(); return }

        populateHeader(measurement)
        populateIdentityTable(measurement)
        populateSignalTable(measurement)
        populateLocationTable(measurement, null, null, measurement.timingAdvance?.let {
            if (measurement.radio == RadioType.LTE && it > 0) it * 78.12 else null
        })
        setupMap(savedInstanceState, measurement)
        loadHistory(measurement)

        binding.buttonHunt.setOnClickListener {
            val intent = android.content.Intent(this, HuntActivity::class.java).apply {
                putExtra(HuntActivity.EXTRA_RADIO, measurement.radio.name)
                measurement.mcc?.let { putExtra(HuntActivity.EXTRA_MCC, it) }
                measurement.mnc?.let { putExtra(HuntActivity.EXTRA_MNC, it) }
                measurement.tacLac?.let { putExtra(HuntActivity.EXTRA_TAC_LAC, it) }
                measurement.cid?.let { putExtra(HuntActivity.EXTRA_CID, it) }
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

    private fun loadHistory(current: CellMeasurement) {
        val mcc = current.mcc ?: return
        val mnc = current.mnc ?: return
        val tacLac = current.tacLac ?: return
        val cid = current.cid ?: return

        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                db.measurementDao().getMeasurementsByCell(mcc, mnc, tacLac, cid)
                    .map { EntityMapper.toDomain(it) }
                    .sortedByDescending { it.timestamp }
                    .take(50)
            }

            // Check tower cache for known location
            val cachedTower = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                db.towerCacheDao().findTower(current.radio.name, mcc, mnc, tacLac, cid)
            }

            // Try to estimate tower location from measurement spread
            val towerLat: Double?
            val towerLon: Double?
            val allPoints = (history + current)

            if (cachedTower?.latitude != null && cachedTower.longitude != null) {
                towerLat = cachedTower.latitude
                towerLon = cachedTower.longitude
            } else {
                val estimated = TowerLocator.estimate(allPoints)
                towerLat = estimated?.first
                towerLon = estimated?.second

                // 2.3 — persist the learned tower position when we have
                // enough samples and nothing authoritative is cached yet.
                if (estimated != null && cachedTower == null && allPoints.size >= 5) {
                    val learned = CellTower(
                        radio = current.radio,
                        mcc = mcc,
                        mnc = mnc,
                        tacLac = tacLac,
                        cid = cid,
                        latitude = estimated.first,
                        longitude = estimated.second,
                        samples = allPoints.size,
                        source = "learned"
                    )
                    val repo = TowerCacheRepository(
                        AppDatabase.getInstance(applicationContext).towerCacheDao()
                    )
                    withContext(Dispatchers.IO) { repo.learnPosition(learned) }
                }
            }

            // Estimate distance from timing advance
            val distanceM = current.timingAdvance?.let {
                if (current.radio == RadioType.LTE && it > 0) it * 78.12 else null
            }

            // Update the location table with correct info
            binding.tableLocation.removeAllViews()
            populateLocationTable(current, towerLat, towerLon, distanceM)

            binding.textHistoryCount.text = "${history.size} measurements recorded for this tower"

            if (history.isNotEmpty()) {
                binding.recyclerHistory.layoutManager = LinearLayoutManager(this@TowerDetailActivity)
                binding.recyclerHistory.adapter = HistoryAdapter(history)
            }

            // Update map with tower location or measurement spread
            updateMapWithTowerInfo(current, towerLat, towerLon, distanceM, allPoints)
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
                val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
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
                    org.maplibre.geojson.Feature.fromGeometry(
                        org.maplibre.geojson.Point.fromLngLat(m.longitude, m.latitude)
                    ).apply {
                        addNumberProperty("rsrp", (m.rsrp ?: -120).toDouble())
                    }
                }
                val fc = org.maplibre.geojson.FeatureCollection.fromFeatures(features)

                style.addSource(org.maplibre.android.style.sources.GeoJsonSource("history", fc))

                val layer = org.maplibre.android.style.layers.CircleLayer("history-points", "history")
                layer.setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.circleRadius(5f),
                    org.maplibre.android.style.layers.PropertyFactory.circleColor(
                        org.maplibre.android.style.expressions.Expression.interpolate(
                            org.maplibre.android.style.expressions.Expression.linear(),
                            org.maplibre.android.style.expressions.Expression.get("rsrp"),
                            org.maplibre.android.style.expressions.Expression.stop(-120, org.maplibre.android.style.expressions.Expression.color(Color.parseColor("#D50000"))),
                            org.maplibre.android.style.expressions.Expression.stop(-100, org.maplibre.android.style.expressions.Expression.color(Color.parseColor("#FF6D00"))),
                            org.maplibre.android.style.expressions.Expression.stop(-90, org.maplibre.android.style.expressions.Expression.color(Color.parseColor("#FFD600"))),
                            org.maplibre.android.style.expressions.Expression.stop(-80, org.maplibre.android.style.expressions.Expression.color(Color.parseColor("#00C853")))
                        )
                    ),
                    org.maplibre.android.style.layers.PropertyFactory.circleOpacity(0.7f),
                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(1f),
                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor(Color.WHITE)
                )
                style.addLayer(layer)
            }
        }
    }

    // --- Table helpers ---

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
        row.addView(TextView(this).apply {
            text = labelText
            textSize = 13f
            setTextColor(Color.parseColor(if (helpKey != null) "#2962FF" else "#888888"))
            setPadding(0, 0, 24, 0)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(valueColor ?: Color.parseColor("#222222"))
            if (valueColor != null) setTypeface(null, android.graphics.Typeface.BOLD)
        })
        if (helpKey != null) {
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { showHelp(helpKey) }
        }
        table.addView(row)
    }

    private fun addRowWithQuality(
        table: TableLayout,
        label: String,
        value: String,
        quality: com.terrycollins.cellid.domain.model.SignalQuality,
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
