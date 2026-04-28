package com.celltowerid.android.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.celltowerid.android.BuildConfig
import com.celltowerid.android.R
import com.celltowerid.android.databinding.ActivityLocateBinding
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.ui.viewmodel.LocateViewModel
import com.celltowerid.android.util.MapAttributionBinder
import com.celltowerid.android.util.SignalClassifier
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class LocateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocateBinding
    private var mapView: MapView? = null
    private var maplibreMap: MapLibreMap? = null
    private var layersInitialized = false
    private var firstCamera = true

    private val viewModel: LocateViewModel by viewModels()

    companion object {
        const val EXTRA_RADIO = "extra_locate_radio"
        const val EXTRA_MCC = "extra_locate_mcc"
        const val EXTRA_MNC = "extra_locate_mnc"
        const val EXTRA_TAC_LAC = "extra_locate_tac"
        const val EXTRA_CID = "extra_locate_cid"

        private const val SOURCE_TRAIL = "locate-trail"
        private const val LAYER_TRAIL = "locate-trail-points"
        private const val SOURCE_TOWER = "locate-tower"
        private const val LAYER_TOWER = "locate-tower-point"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        binding = ActivityLocateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val radioStr = intent.getStringExtra(EXTRA_RADIO) ?: run { finish(); return }
        val radio = RadioType.fromString(radioStr)
        val mcc = if (intent.hasExtra(EXTRA_MCC)) intent.getIntExtra(EXTRA_MCC, -1) else null
        val mnc = if (intent.hasExtra(EXTRA_MNC)) intent.getIntExtra(EXTRA_MNC, -1) else null
        val tac = if (intent.hasExtra(EXTRA_TAC_LAC)) intent.getIntExtra(EXTRA_TAC_LAC, -1) else null
        val cid = if (intent.hasExtra(EXTRA_CID)) intent.getLongExtra(EXTRA_CID, -1L) else null

        binding.toolbar.title = "Locate ${radio.name}" + (cid?.let { " · CID $it" } ?: "")

        setupMap(savedInstanceState)
        MapAttributionBinder.bind(binding.mapAttribution)
        binding.buttonReset.setOnClickListener { viewModel.reset() }

        viewModel.state.observe(this) { renderState(it) }
        viewModel.start(radio, mcc, mnc, tac, cid)
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { map ->
            maplibreMap = map
            map.setStyle(BuildConfig.TILE_STYLE_URL) { style ->
                initLayers(style)
            }
        }
    }

    private fun initLayers(style: Style) {
        if (layersInitialized) return
        style.addSource(GeoJsonSource(SOURCE_TRAIL, FeatureCollection.fromFeatures(emptyArray())))
        val trail = CircleLayer(LAYER_TRAIL, SOURCE_TRAIL).apply {
            setProperties(
                PropertyFactory.circleRadius(6f),
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
                PropertyFactory.circleOpacity(0.8f),
                PropertyFactory.circleStrokeWidth(1f),
                PropertyFactory.circleStrokeColor(Color.WHITE)
            )
        }
        style.addLayer(trail)

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

    private fun renderState(state: LocateViewModel.LocateState) {
        // Big number
        if (state.lostContact || state.smoothedRsrp == null) {
            binding.textRsrp.text = "--"
            binding.textRsrp.setTextColor(Color.parseColor("#888888"))
            binding.textStatus.text = getString(R.string.locate_lost_contact)
            binding.textDelta.text = ""
            binding.textDistance.text = ""
            binding.imageArrow.alpha = 0.25f
            binding.imageArrow.contentDescription = getString(R.string.locate_lost_contact)
        } else {
            val smoothed = state.smoothedRsrp.toInt()
            binding.textRsrp.text = smoothed.toString()
            binding.textRsrp.setTextColor(
                Color.parseColor(SignalClassifier.classifyLteRsrp(smoothed).colorHex)
            )

            val status = when {
                Math.abs(state.deltaDb) < 2.0 -> getString(R.string.locate_steady)
                state.deltaDb > 0 -> getString(R.string.locate_hotter)
                else -> getString(R.string.locate_colder)
            }
            binding.textStatus.text = status

            binding.textDelta.text = String.format(
                "Δ %+.1f dB / 10s · raw %d dBm",
                state.deltaDb,
                state.rawRsrp ?: smoothed
            )

            val distance = state.distanceMeters
            binding.textDistance.text = distance?.let {
                if (it < 1000) String.format("~%.0f m away", it)
                else String.format("~%.1f km away", it / 1000.0)
            } ?: ""

            // Arrow
            val bearing = state.bearing
            if (bearing != null) {
                binding.imageArrow.alpha = 1.0f
                binding.imageArrow.rotation = bearing.toFloat()
                binding.imageArrow.contentDescription = "Direction arrow pointing ${bearing.toInt()} degrees"
            } else {
                binding.imageArrow.alpha = 0.3f
                binding.imageArrow.rotation = 0f
                binding.imageArrow.contentDescription = getString(R.string.locate_waiting_for_fix)
                if (state.waypoints.size < 3) {
                    binding.textStatus.text = getString(R.string.locate_waiting_for_fix)
                }
            }
        }

        // Map updates
        val map = maplibreMap ?: return
        val style = map.style ?: return
        if (!layersInitialized) return

        val trailFeatures = state.waypoints.map { wp ->
            Feature.fromGeometry(Point.fromLngLat(wp.lon, wp.lat)).apply {
                addNumberProperty("rsrp", wp.rsrpDbm.toDouble())
            }
        }
        style.getSourceAs<GeoJsonSource>(SOURCE_TRAIL)
            ?.setGeoJson(FeatureCollection.fromFeatures(trailFeatures))

        val towerFeatures = state.estimatedTower?.let { (lat, lon) ->
            listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
        } ?: emptyList()
        style.getSourceAs<GeoJsonSource>(SOURCE_TOWER)
            ?.setGeoJson(FeatureCollection.fromFeatures(towerFeatures))

        if (firstCamera) {
            state.currentLocation?.let { (lat, lon) ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(lat, lon))
                    .zoom(16.0)
                    .build()
                firstCamera = false
            }
        }
    }

    // MapView lifecycle
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        mapView?.onDestroy()
        super.onDestroy()
    }
}
