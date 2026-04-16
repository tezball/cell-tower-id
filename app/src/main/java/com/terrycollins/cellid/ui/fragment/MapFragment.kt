package com.terrycollins.cellid.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.terrycollins.cellid.BuildConfig
import com.terrycollins.cellid.R
import com.terrycollins.cellid.databinding.FragmentMapBinding
import com.terrycollins.cellid.domain.model.CellMeasurement
import com.terrycollins.cellid.domain.model.CellTower
import com.terrycollins.cellid.domain.model.RadioType
import com.terrycollins.cellid.ui.viewmodel.MapViewModel
import com.terrycollins.cellid.util.SignalClassifier
import com.terrycollins.cellid.util.UsCarriers
import android.util.Log
import org.maplibre.android.MapLibre
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val mapViewModel: MapViewModel by viewModels()
    private var mapView: MapView? = null
    private var maplibreMap: MapLibreMap? = null
    private var layersInitialized = false
    private var towerLayerInitialized = false
    private var hasCenteredOnUser = false
    private lateinit var fusedLocation: FusedLocationProviderClient

    companion object {
        private const val TAG = "CellID.MapFragment"
        private const val SOURCE_MEASUREMENTS = "measurements"
        private const val LAYER_HEATMAP = "measurements-heat"
        private const val LAYER_POINTS = "measurements-points"
        private const val SOURCE_TOWERS = "towers"
        private const val LAYER_TOWERS = "towers-points"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Must initialize MapLibre BEFORE inflating the layout that contains MapView
        MapLibre.getInstance(requireContext())
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocation = LocationServices.getFusedLocationProviderClient(requireContext())
        setupMap(savedInstanceState)
        setupFilters()
        setupMyLocationButton()
        observeData()

        // Load initial data
        mapViewModel.loadRecentMeasurements()
        mapViewModel.loadAllTowers()
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { map ->
            maplibreMap = map

            map.setStyle(BuildConfig.TILE_STYLE_URL) { style ->
                enableLocationComponent(style)
                centerOnCurrentLocation()
            }

            map.addOnCameraIdleListener {
                loadDataForVisibleRegion()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerOnCurrentLocation() {
        if (hasCenteredOnUser) return
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val map = maplibreMap ?: return
        fusedLocation.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && !hasCenteredOnUser) {
                hasCenteredOnUser = true
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(15.0)
                    .build()
            } else if (loc == null) {
                fusedLocation.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { fresh ->
                        if (fresh != null && !hasCenteredOnUser) {
                            hasCenteredOnUser = true
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(fresh.latitude, fresh.longitude))
                                .zoom(15.0)
                                .build()
                        }
                    }
            }
        }
    }

    private fun enableLocationComponent(style: Style) {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationComponent = maplibreMap?.locationComponent
            locationComponent?.activateLocationComponent(
                LocationComponentActivationOptions
                    .builder(requireContext(), style)
                    .build()
            )
            locationComponent?.isLocationComponentEnabled = true
            locationComponent?.cameraMode = CameraMode.TRACKING
        }
    }

    private fun loadDataForVisibleRegion() {
        val bounds = maplibreMap?.projection?.visibleRegion?.latLngBounds ?: return
        mapViewModel.loadMeasurementsInArea(
            bounds.latitudeSouth, bounds.latitudeNorth,
            bounds.longitudeWest, bounds.longitudeEast
        )
    }

    private fun setupMyLocationButton() {
        binding.fabMyLocation.setOnClickListener {
            val map = maplibreMap ?: return@setOnClickListener
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Snackbar.make(binding.root, R.string.permission_location_required, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val location = map.locationComponent
                .takeIf { it.isLocationComponentActivated && it.isLocationComponentEnabled }
                ?.lastKnownLocation
            if (location == null) {
                Snackbar.make(binding.root, "Waiting for location fix…", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            map.locationComponent.cameraMode = CameraMode.TRACKING
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0)
            )
        }
    }

    private fun setupFilters() {
        binding.chipAll.isChecked = true
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(R.id.chip_lte) -> RadioType.LTE
                checkedIds.contains(R.id.chip_nr) -> RadioType.NR
                checkedIds.contains(R.id.chip_gsm) -> RadioType.GSM
                checkedIds.contains(R.id.chip_wcdma) -> RadioType.WCDMA
                else -> null
            }
            mapViewModel.setRadioTypeFilter(filter)
        }
    }

    private fun observeData() {
        mapViewModel.measurements.observe(viewLifecycleOwner) { measurements ->
            updateMapMarkers(measurements)
            updateInfoCard(measurements)
        }
        mapViewModel.towers.observe(viewLifecycleOwner) { towers ->
            updateTowerMarkers(towers)
        }
    }

    private fun updateTowerMarkers(towers: List<CellTower>) {
        val map = maplibreMap ?: return
        val style = map.style ?: return

        val deduped = com.terrycollins.cellid.util.TowerDedup.collapseLteByEnb(towers)
        val features = deduped.mapNotNull { t ->
            val lat = t.latitude ?: return@mapNotNull null
            val lon = t.longitude ?: return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("radio", t.radio.name)
                addNumberProperty("cid", t.cid.toDouble())
                addNumberProperty("mcc", t.mcc.toDouble())
                addNumberProperty("mnc", t.mnc.toDouble())
            }
        }
        val fc = FeatureCollection.fromFeatures(features)

        if (!towerLayerInitialized) {
            try {
                style.addSource(GeoJsonSource(SOURCE_TOWERS, fc))
                val layer = CircleLayer(LAYER_TOWERS, SOURCE_TOWERS).apply {
                    setProperties(
                        PropertyFactory.circleRadius(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(6, 2f),
                                Expression.stop(14, 6f),
                                Expression.stop(18, 10f)
                            )
                        ),
                        PropertyFactory.circleColor(
                            Expression.match(
                                Expression.get("radio"),
                                Expression.color(Color.parseColor("#2962FF")),
                                Expression.stop("LTE", Expression.color(Color.parseColor("#2962FF"))),
                                Expression.stop("NR", Expression.color(Color.parseColor("#AA00FF"))),
                                Expression.stop("GSM", Expression.color(Color.parseColor("#00C853"))),
                                Expression.stop("WCDMA", Expression.color(Color.parseColor("#FF6D00")))
                            )
                        ),
                        PropertyFactory.circleOpacity(0.75f),
                        PropertyFactory.circleStrokeWidth(1f),
                        PropertyFactory.circleStrokeColor(Color.WHITE)
                    )
                }
                style.addLayer(layer)
                towerLayerInitialized = true
                Log.d(TAG, "Tower layer initialized with ${features.size} towers")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing tower layer", e)
            }
        } else {
            try {
                style.getSourceAs<GeoJsonSource>(SOURCE_TOWERS)?.setGeoJson(fc)
                Log.d(TAG, "Tower source updated with ${features.size} towers")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating tower source", e)
            }
        }
    }

    private fun updateMapMarkers(measurements: List<CellMeasurement>) {
        val map = maplibreMap ?: return
        val style = map.style ?: return

        Log.d(TAG, "Updating map with ${measurements.size} measurements")

        // Build GeoJSON features
        val features = if (measurements.isEmpty()) {
            emptyList()
        } else {
            measurements.map { m ->
                val quality = SignalClassifier.classify(m)
                Feature.fromGeometry(
                    Point.fromLngLat(m.longitude, m.latitude)
                ).apply {
                    addNumberProperty("rsrp", (m.rsrp ?: -120).toDouble())
                    addStringProperty("radio", m.radio.name)
                    addBooleanProperty("serving", m.isRegistered)
                    addNumberProperty("quality", quality.ordinal.toDouble())
                }
            }
        }

        val featureCollection = FeatureCollection.fromFeatures(features)

        if (!layersInitialized) {
            // First time: create source and layers
            try {
                val source = GeoJsonSource(SOURCE_MEASUREMENTS, featureCollection)
                style.addSource(source)

                val heatmapLayer = HeatmapLayer(LAYER_HEATMAP, SOURCE_MEASUREMENTS)
                heatmapLayer.setProperties(
                    PropertyFactory.heatmapWeight(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.get("rsrp"),
                            Expression.stop(-120, 0),
                            Expression.stop(-80, 1)
                        )
                    ),
                    PropertyFactory.heatmapIntensity(1f),
                    PropertyFactory.heatmapRadius(20f),
                    PropertyFactory.heatmapOpacity(0.6f)
                )
                style.addLayer(heatmapLayer)

                val circleLayer = CircleLayer(LAYER_POINTS, SOURCE_MEASUREMENTS)
                circleLayer.setProperties(
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.zoom(),
                            Expression.stop(10, 2f),
                            Expression.stop(18, 8f)
                        )
                    ),
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
                circleLayer.minZoom = 12f
                style.addLayer(circleLayer)

                layersInitialized = true
                Log.d(TAG, "Map layers initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing map layers", e)
            }
        } else {
            // Subsequent updates: just update the source data in-place
            try {
                val source = style.getSourceAs<GeoJsonSource>(SOURCE_MEASUREMENTS)
                source?.setGeoJson(featureCollection)
                Log.d(TAG, "Map source updated with ${features.size} features")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating map source", e)
            }
        }
    }

    private fun updateInfoCard(measurements: List<CellMeasurement>) {
        val serving = measurements.find { it.isRegistered }
        if (serving != null) {
            binding.infoCard.visibility = View.VISIBLE
            val carrier = if (serving.mcc != null && serving.mnc != null) {
                UsCarriers.getCarrierName(serving.mcc, serving.mnc)
                    ?: "${serving.mcc}/${serving.mnc}"
            } else {
                "Unknown"
            }

            val quality = SignalClassifier.classify(serving)
            binding.textCellInfo.text =
                "${serving.radio.name} | $carrier | CID: ${serving.cid ?: "?"}"
            binding.textSignalInfo.text =
                "RSRP: ${serving.rsrp ?: "?"}dBm | SINR: ${serving.sinr ?: "?"}dB | ${quality.label}"
        } else {
            binding.infoCard.visibility = View.GONE
        }
    }

    // MapView lifecycle methods
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroyView() {
        layersInitialized = false
        towerLayerInitialized = false
        mapView?.onDestroy()
        _binding = null
        super.onDestroyView()
    }

}
