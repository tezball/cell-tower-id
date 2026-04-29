package com.celltowerid.android.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.celltowerid.android.BuildConfig
import com.celltowerid.android.R
import com.celltowerid.android.databinding.FragmentMapBinding
import com.celltowerid.android.domain.model.CellKey
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.CellTower
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.domain.model.SignalQuality
import androidx.lifecycle.lifecycleScope
import com.celltowerid.android.ui.viewmodel.MapViewModel
import com.celltowerid.android.util.AppLog
import com.celltowerid.android.util.OfflineTileManager
import com.celltowerid.android.util.SignalClassifier
import com.celltowerid.android.util.TowerInfoFormatter
import com.celltowerid.android.util.UsCarriers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var towerLayerInitialized = false
    private var hasCenteredOnUser = false
    private var mapStyle: Style? = null
    private var locationComponentEnabled = false
    private var isViewAlive = false
    private var samplerJob: Job? = null
    private var reloadJob: Job? = null
    private var lastLoadedBounds: org.maplibre.android.geometry.LatLngBounds? = null
    private lateinit var fusedLocation: FusedLocationProviderClient

    private var selectedCellKey: CellKey? = null

    companion object {
        private const val TAG = "CellTowerID.MapFragment"
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
        setupTowerInfoCard()
        setupWindowInsets()
        observeData()
        isViewAlive = true
    }

    private fun setupWindowInsets() {
        val density = resources.displayMetrics.density
        val fabMyLocBaseEndPx = (16 * density).toInt()
        val fabMyLocBaseBottomPx = (80 * density).toInt()
        val infoCardBaseEndPx = (72 * density).toInt()
        val infoCardBaseBottomPx = (8 * density).toInt()
        val attributionBaseEndPx = (6 * density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(binding.fabMyLocation) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = fabMyLocBaseEndPx + sys.right
                bottomMargin = fabMyLocBaseBottomPx + sys.bottom
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.infoCard) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = infoCardBaseEndPx + sys.right
                bottomMargin = infoCardBaseBottomPx + sys.bottom
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.filterScroll) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = sys.left, right = sys.right)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mapAttribution) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = attributionBaseEndPx + sys.right
            }
            insets
        }
    }

    private fun setupTowerInfoCard() {
        binding.towerInfoClose.setOnClickListener { hideTowerInfoBox() }
        binding.btnUnpinTower.setOnClickListener {
            val key = selectedCellKey ?: return@setOnClickListener
            mapViewModel.unpinTower(key.radio, key.mcc, key.mnc, key.tacLac, key.cid)
            hideTowerInfoBox()
        }
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        mapView = binding.mapView
        mapView?.onCreate(savedInstanceState)
        loadMapStyle()

        binding.btnRetryMap.setOnClickListener {
            binding.mapErrorOverlay.visibility = View.GONE
            // The prior MapLibreMap instance is discarded on retry; the new
            // getMapAsync callback will hand us a fresh one. Reset so the
            // listeners are reattached to the new map — otherwise camera-idle
            // reload and tap-for-info silently stop working after the first
            // retry.
            listenersAttached = false
            loadMapStyle()
        }
    }

    private var listenersAttached = false

    private fun loadMapStyle() {
        mapView?.getMapAsync { map ->
            if (!isViewAlive) return@getMapAsync
            maplibreMap = map

            // Belt-and-braces: we overlay our own attribution view in the
            // layout, but enable MapLibre's built-in one too so the Info
            // popup lists all layer sources (OSM, OpenFreeMap) for
            // compliance with ODbL/OpenFreeMap ToS.
            map.uiSettings.isAttributionEnabled = true
            com.celltowerid.android.util.MapAttributionBinder
                .bind(binding.mapAttribution)

            if (!listenersAttached) {
                mapView?.addOnDidFailLoadingMapListener {
                    if (!isViewAlive) return@addOnDidFailLoadingMapListener
                    AppLog.e(TAG, "Map failed to load")
                    binding.mapErrorOverlay.visibility = View.VISIBLE
                }
                map.addOnCameraIdleListener {
                    if (!isViewAlive || mapStyle == null) return@addOnCameraIdleListener
                    val lc = map.locationComponent
                    val mode = if (lc.isLocationComponentActivated) cameraModeName(lc.cameraMode) else "INACTIVE"
                    val target = map.cameraPosition.target
                    AppLog.d(TAG, "onCameraIdle: mode=$mode target=${target?.latitude},${target?.longitude} zoom=${map.cameraPosition.zoom}")
                    scheduleReload()
                }
                map.addOnCameraMoveStartedListener { reason ->
                    if (!isViewAlive) return@addOnCameraMoveStartedListener
                    val lc = map.locationComponent
                    val mode = if (lc.isLocationComponentActivated) cameraModeName(lc.cameraMode) else "INACTIVE"
                    AppLog.d(TAG, "onCameraMoveStarted: reason=${moveReasonName(reason)} mode=$mode")
                    hideTowerInfoBox()
                }
                map.addOnMapClickListener { latLng ->
                    if (!isViewAlive || !towerLayerInitialized) return@addOnMapClickListener false
                    val screenPoint = map.projection.toScreenLocation(latLng)
                    val hits = map.queryRenderedFeatures(screenPoint, LAYER_TOWERS)
                    val feature = hits.firstOrNull()
                    if (feature != null) {
                        showTowerInfoBox(feature)
                        true
                    } else {
                        hideTowerInfoBox()
                        false
                    }
                }
                listenersAttached = true
            }

            map.setStyle(BuildConfig.TILE_STYLE_URL) { style ->
                if (!isViewAlive) return@setStyle
                mapStyle = style
                binding.mapErrorOverlay.visibility = View.GONE
                enableLocationComponent(style)
                centerOnCurrentLocation()
                // Defer initial data load to next frame so layers are fully attached
                mapView?.post {
                    if (isViewAlive) {
                        mapViewModel.loadRecentMeasurements()
                        mapViewModel.loadAllTowers()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerOnCurrentLocation() {
        AppLog.d(TAG, "centerOnCurrentLocation: entry hasCenteredOnUser=$hasCenteredOnUser isViewAlive=$isViewAlive")
        if (!isViewAlive || hasCenteredOnUser) return
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLog.d(TAG, "centerOnCurrentLocation: no permission")
            return
        }
        val map = maplibreMap ?: return
        fusedLocation.lastLocation.addOnSuccessListener { loc ->
            if (!isViewAlive) return@addOnSuccessListener
            if (loc != null && !hasCenteredOnUser) {
                hasCenteredOnUser = true
                AppLog.d(TAG, "centerOnCurrentLocation: cached loc=${loc.latitude},${loc.longitude}")
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(15.0)
                    .build()
                // Re-assert TRACKING: direct cameraPosition assignment resets cameraMode to NONE
                map.locationComponent.cameraMode = CameraMode.TRACKING
                AppLog.d(TAG, "centerOnCurrentLocation: post-assign mode=${cameraModeName(map.locationComponent.cameraMode)}")
                mapView?.post { loadDataForVisibleRegion() }
            } else if (loc == null) {
                AppLog.d(TAG, "centerOnCurrentLocation: cached null, requesting fresh fix")
                fusedLocation.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { fresh ->
                        if (!isViewAlive) return@addOnSuccessListener
                        if (fresh != null && !hasCenteredOnUser) {
                            hasCenteredOnUser = true
                            AppLog.d(TAG, "centerOnCurrentLocation: fresh loc=${fresh.latitude},${fresh.longitude}")
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(fresh.latitude, fresh.longitude))
                                .zoom(15.0)
                                .build()
                            map.locationComponent.cameraMode = CameraMode.TRACKING
                            AppLog.d(TAG, "centerOnCurrentLocation: post-assign mode=${cameraModeName(map.locationComponent.cameraMode)}")
                            mapView?.post { loadDataForVisibleRegion() }
                        } else {
                            AppLog.w(TAG, "centerOnCurrentLocation: fresh fix null")
                        }
                    }
            }
        }
    }

    private fun enableLocationComponent(style: Style) {
        if (!isViewAlive || locationComponentEnabled) return
        val locationComponent = maplibreMap?.locationComponent ?: return
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLog.d(TAG, "enableLocationComponent: permission not granted, skipping")
            return
        }
        AppLog.d(TAG, "enableLocationComponent: activating")
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions
                .builder(requireContext(), style)
                .build()
        )
        locationComponent.isLocationComponentEnabled = true
        locationComponent.cameraMode = CameraMode.TRACKING
        locationComponentEnabled = true
        AppLog.d(TAG, "enableLocationComponent: activated (after mode=${cameraModeName(locationComponent.cameraMode)})")
    }

    private fun scheduleReload() {
        if (!isViewAlive) return
        reloadJob?.cancel()
        reloadJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            loadDataForVisibleRegion()
        }
    }

    private fun loadDataForVisibleRegion() {
        if (!isViewAlive) return
        val bounds = maplibreMap?.projection?.visibleRegion?.latLngBounds ?: return
        // Skip if bounds are too large (world view before camera centers on user)
        val latSpan = bounds.latitudeNorth - bounds.latitudeSouth
        val lonSpan = bounds.longitudeEast - bounds.longitudeWest
        if (latSpan > 2.0 || lonSpan > 2.0) {
            AppLog.d(TAG, "loadDataForVisibleRegion: skip, bounds too large (${latSpan}x${lonSpan})")
            return
        }
        // Skip if bounds haven't moved enough to matter (GPS jitter in TRACKING mode)
        lastLoadedBounds?.let { prev ->
            val dLatN = Math.abs(bounds.latitudeNorth - prev.latitudeNorth)
            val dLatS = Math.abs(bounds.latitudeSouth - prev.latitudeSouth)
            val dLonE = Math.abs(bounds.longitudeEast - prev.longitudeEast)
            val dLonW = Math.abs(bounds.longitudeWest - prev.longitudeWest)
            val threshold = Math.min(latSpan, lonSpan) * 0.1
            if (dLatN < threshold && dLatS < threshold && dLonE < threshold && dLonW < threshold) {
                return
            }
        }
        lastLoadedBounds = bounds
        mapViewModel.loadMeasurementsInArea(
            bounds.latitudeSouth, bounds.latitudeNorth,
            bounds.longitudeWest, bounds.longitudeEast
        )
        // Cache tiles for this area for offline use
        OfflineTileManager.cacheVisibleRegion(requireContext(), bounds, BuildConfig.TILE_STYLE_URL)
    }

    private fun cameraModeName(mode: Int): String = when (mode) {
        CameraMode.NONE -> "NONE"
        CameraMode.NONE_COMPASS -> "NONE_COMPASS"
        CameraMode.NONE_GPS -> "NONE_GPS"
        CameraMode.TRACKING -> "TRACKING"
        CameraMode.TRACKING_COMPASS -> "TRACKING_COMPASS"
        CameraMode.TRACKING_GPS -> "TRACKING_GPS"
        CameraMode.TRACKING_GPS_NORTH -> "TRACKING_GPS_NORTH"
        else -> "UNKNOWN($mode)"
    }

    private fun moveReasonName(reason: Int): String = when (reason) {
        MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE -> "GESTURE"
        MapLibreMap.OnCameraMoveStartedListener.REASON_API_ANIMATION -> "API_ANIMATION"
        MapLibreMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION -> "DEVELOPER_ANIMATION"
        else -> "UNKNOWN($reason)"
    }

    @SuppressLint("MissingPermission")
    private fun startDiagnosticSampler() {
        // Diagnostic-only: logs location + camera state every 2 seconds.
        // Off in release builds — contributes disk/battery cost and logs
        // precise coordinates.
        if (!BuildConfig.DEBUG) return
        samplerJob?.cancel()
        samplerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(2000)
                val map = maplibreMap ?: continue
                if (!isViewAlive) continue
                try {
                    val lc = map.locationComponent
                    val mode = if (lc.isLocationComponentActivated) cameraModeName(lc.cameraMode) else "INACTIVE"
                    val target = map.cameraPosition.target
                    val lastLoc = if (lc.isLocationComponentActivated && lc.isLocationComponentEnabled) lc.lastKnownLocation else null
                    val locStr = lastLoc?.let {
                        val age = System.currentTimeMillis() - it.time
                        "${it.latitude},${it.longitude} age=${age}ms"
                    } ?: "null"
                    val tStr = target?.let { "${it.latitude},${it.longitude}" } ?: "null"
                    val dist = if (lastLoc != null && target != null) {
                        val out = FloatArray(1)
                        android.location.Location.distanceBetween(
                            lastLoc.latitude, lastLoc.longitude,
                            target.latitude, target.longitude, out
                        )
                        "%.1fm".format(out[0])
                    } else "?"
                    AppLog.d(TAG, "sample: mode=$mode target=$tStr loc=$locStr dist=$dist")
                } catch (t: Throwable) {
                    AppLog.w(TAG, "sample failed", t)
                }
            }
        }
    }

    private fun stopDiagnosticSampler() {
        samplerJob?.cancel()
        samplerJob = null
    }

    private fun setupMyLocationButton() {
        binding.fabMyLocation.setOnClickListener {
            AppLog.d(TAG, "FAB clicked: maplibreMap=$maplibreMap isViewAlive=$isViewAlive")
            val map = maplibreMap ?: run {
                AppLog.w(TAG, "FAB: maplibreMap is null, ignoring")
                return@setOnClickListener
            }
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AppLog.d(TAG, "FAB: location permission not granted")
                Snackbar.make(binding.root, R.string.permission_location_required, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val locComponent = map.locationComponent
            val fabMode = if (locComponent.isLocationComponentActivated) cameraModeName(locComponent.cameraMode) else "INACTIVE"
            AppLog.d(TAG, "FAB: activated=${locComponent.isLocationComponentActivated} enabled=${locComponent.isLocationComponentEnabled} mode=$fabMode")
            val location = locComponent
                .takeIf { it.isLocationComponentActivated && it.isLocationComponentEnabled }
                ?.lastKnownLocation
            AppLog.d(TAG, "FAB: location=$location")
            if (location == null) {
                Snackbar.make(binding.root, R.string.waiting_for_location_fix, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppLog.d(TAG, "FAB: animating to ${location.latitude},${location.longitude}")
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
            if (!isViewAlive) return@observe
            updateInfoCard(measurements)
        }
        mapViewModel.towers.observe(viewLifecycleOwner) {
            if (!isViewAlive) return@observe
            renderTowerLayer()
        }
        // Re-render when best-readings refresh so the dot color and info card
        // pick up new strongest-signal data without waiting for the next
        // tower-list emission.
        mapViewModel.bestReadings.observe(viewLifecycleOwner) {
            if (!isViewAlive) return@observe
            renderTowerLayer()
        }
        // Reactive pin refresh: when the user pins/unpins a tower from the
        // Cell List (or anywhere else), pinnedTowerEntities emits. Re-run
        // loadAllTowers so the map reflects the change within one frame
        // instead of waiting up to 15 s for the auto-refresh tick.
        mapViewModel.pinnedTowerEntities().observe(viewLifecycleOwner) {
            if (!isViewAlive) return@observe
            mapViewModel.loadAllTowers()
        }
    }

    private fun renderTowerLayer() {
        val towers = mapViewModel.towers.value ?: return
        updateTowerMarkers(towers)
    }

    private fun updateTowerMarkers(towers: List<CellTower>) {
        if (!isViewAlive) return
        val style = mapStyle ?: return
        val startNs = System.nanoTime()
        val modeBefore = maplibreMap?.locationComponent?.let { if (it.isLocationComponentActivated) cameraModeName(it.cameraMode) else "INACTIVE" } ?: "null"

        val bestMap = mapViewModel.bestReadings.value ?: emptyMap()
        // For LTE the dedup collapses sectors into one dot; aggregate the
        // best reading across all sectors of an eNB so the dot reflects the
        // strongest reading observed for the eNB, not just the kept sector.
        val lteBestByEnb = mutableMapOf<Triple<Int, Int, Long>, CellMeasurement>()
        for (t in towers) {
            if (t.radio != RadioType.LTE) continue
            val m = bestMap[CellKey.of(t)] ?: continue
            val enbKey = Triple(t.mcc, t.mnc, t.cid shr 8)
            val current = lteBestByEnb[enbKey]
            if (current == null || signalDbm(m) > signalDbm(current)) {
                lteBestByEnb[enbKey] = m
            }
        }

        val deduped = com.celltowerid.android.util.TowerDedup.collapseLteByEnb(towers)
        val features = deduped.mapNotNull { t ->
            val lat = t.latitude ?: return@mapNotNull null
            val lon = t.longitude ?: return@mapNotNull null
            val best = if (t.radio == RadioType.LTE) {
                lteBestByEnb[Triple(t.mcc, t.mnc, t.cid shr 8)]
            } else {
                bestMap[CellKey.of(t)]
            }
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("radio", t.radio.name)
                addNumberProperty("cid", t.cid.toDouble())
                addNumberProperty("mcc", t.mcc.toDouble())
                addNumberProperty("mnc", t.mnc.toDouble())
                addNumberProperty("tac_lac", t.tacLac.toDouble())
                addNumberProperty("latitude", lat)
                addNumberProperty("longitude", lon)
                addBooleanProperty("is_pinned", t.isPinned)
                t.rangeMeters?.let { addNumberProperty("range_meters", it.toDouble()) }
                val signal = best?.let { it.rsrp ?: it.rssi }
                if (best != null && signal != null) {
                    addNumberProperty("best_rsrp", signal.toDouble())
                    addNumberProperty("quality_ordinal", SignalClassifier.classify(best).ordinal.toDouble())
                    addNumberProperty("best_timestamp_ms", best.timestamp.toDouble())
                    best.rsrq?.let { addNumberProperty("best_rsrq", it.toDouble()) }
                    best.sinr?.let { addNumberProperty("best_sinr", it.toDouble()) }
                    addBooleanProperty("has_best", true)
                } else {
                    addBooleanProperty("has_best", false)
                }
            }
        }
        val fc = FeatureCollection.fromFeatures(features)

        if (!towerLayerInitialized) {
            try {
                style.addSource(GeoJsonSource(SOURCE_TOWERS, fc))
                val layer = CircleLayer(LAYER_TOWERS, SOURCE_TOWERS).apply {
                    setProperties(
                        PropertyFactory.circleRadius(
                            Expression.product(
                                Expression.switchCase(
                                    Expression.eq(Expression.get("is_pinned"), Expression.literal(true)),
                                    Expression.literal(1.8f),
                                    Expression.literal(1.0f)
                                ),
                                Expression.interpolate(
                                    Expression.linear(),
                                    Expression.zoom(),
                                    Expression.stop(6, 2f),
                                    Expression.stop(14, 6f),
                                    Expression.stop(18, 10f)
                                )
                            )
                        ),
                        PropertyFactory.circleColor(
                            Expression.switchCase(
                                Expression.eq(Expression.get("has_best"), Expression.literal(true)),
                                qualityOrdinalColorExpression(),
                                radioTypeColorExpression()
                            )
                        ),
                        PropertyFactory.circleOpacity(0.75f),
                        PropertyFactory.circleStrokeWidth(
                            Expression.switchCase(
                                Expression.eq(Expression.get("is_pinned"), Expression.literal(true)),
                                Expression.literal(3f),
                                Expression.literal(1f)
                            )
                        ),
                        PropertyFactory.circleStrokeColor(
                            Expression.switchCase(
                                Expression.eq(Expression.get("is_pinned"), Expression.literal(true)),
                                Expression.color(Color.parseColor("#FFD600")),
                                Expression.color(Color.WHITE)
                            )
                        )
                    )
                }
                style.addLayer(layer)
                towerLayerInitialized = true
                val elapsed = (System.nanoTime() - startNs) / 1_000_000
                val modeAfter = maplibreMap?.locationComponent?.let { if (it.isLocationComponentActivated) cameraModeName(it.cameraMode) else "INACTIVE" } ?: "null"
                AppLog.d(TAG, "updateTowerMarkers: init n=${features.size} took=${elapsed}ms mode=$modeBefore->$modeAfter")
            } catch (e: Exception) {
                AppLog.e(TAG, "Error initializing tower layer", e)
            }
        } else {
            try {
                style.getSourceAs<GeoJsonSource>(SOURCE_TOWERS)?.setGeoJson(fc)
                val elapsed = (System.nanoTime() - startNs) / 1_000_000
                val modeAfter = maplibreMap?.locationComponent?.let { if (it.isLocationComponentActivated) cameraModeName(it.cameraMode) else "INACTIVE" } ?: "null"
                AppLog.d(TAG, "updateTowerMarkers: update n=${features.size} took=${elapsed}ms mode=$modeBefore->$modeAfter")
            } catch (e: Exception) {
                AppLog.e(TAG, "Error updating tower source", e)
            }
        }
    }

    private fun showTowerInfoBox(feature: Feature) {
        if (!isViewAlive || _binding == null) return
        val props = feature.properties() ?: return
        val map = maplibreMap ?: return

        val radio = props.get("radio")?.asString ?: return
        val cid = props.get("cid")?.asLong ?: return
        val mcc = props.get("mcc")?.asInt ?: return
        val mnc = props.get("mnc")?.asInt ?: return
        val tacLac = props.get("tac_lac")?.asInt ?: 0
        val latitude = props.get("latitude")?.asDouble ?: return
        val longitude = props.get("longitude")?.asDouble ?: return
        val rangeMeters = props.get("range_meters")?.asInt
        val isPinned = props.get("is_pinned")?.asBoolean == true
        val hasBest = props.get("has_best")?.asBoolean == true

        binding.towerInfoTitle.text = TowerInfoFormatter.formatTitle(radio, mcc, mnc)
        binding.towerInfoIdentity.text = TowerInfoFormatter.formatIdentity(radio, cid, tacLac)
        binding.towerInfoLocation.text = TowerInfoFormatter.formatLocation(latitude, longitude, rangeMeters)
        binding.btnUnpinTower.visibility = if (isPinned) View.VISIBLE else View.GONE

        if (hasBest) {
            val bestRsrp = props.get("best_rsrp")?.asInt
            val timestampMs = props.get("best_timestamp_ms")?.asLong ?: 0L
            val text = TowerInfoFormatter.formatBestReading(
                rsrp = bestRsrp,
                rssi = null,
                timestampMs = timestampMs,
                nowMs = System.currentTimeMillis()
            )
            if (text != null) {
                binding.towerInfoBestSignal.text = text
                val qualityOrdinal = props.get("quality_ordinal")?.asInt
                val color = qualityOrdinal?.let { ord ->
                    SignalQuality.values().getOrNull(ord)?.colorHex
                } ?: SignalQuality.NO_SIGNAL.colorHex
                binding.towerInfoBestSignal.setTextColor(Color.parseColor(color))
                binding.towerInfoBestSignal.visibility = View.VISIBLE
            } else {
                binding.towerInfoBestSignal.visibility = View.GONE
            }
        } else {
            binding.towerInfoBestSignal.visibility = View.GONE
        }

        selectedCellKey = CellKey(
            radio = RadioType.fromString(radio),
            mcc = mcc,
            mnc = mnc,
            tacLac = tacLac,
            cid = cid
        )

        binding.towerInfoCard.visibility = View.VISIBLE
        binding.towerInfoCard.post { positionTowerInfoCard(map, latitude, longitude) }
    }

    private fun positionTowerInfoCard(map: MapLibreMap, latitude: Double, longitude: Double) {
        if (!isViewAlive || _binding == null) return
        val card = binding.towerInfoCard
        val parent = card.parent as? View ?: return
        val anchor = map.projection.toScreenLocation(LatLng(latitude, longitude))

        val cardWidth = card.width.toFloat()
        val cardHeight = card.height.toFloat()
        val offsetAboveDot = 12f

        val parentWidth = parent.width.toFloat()
        val parentHeight = parent.height.toFloat()

        val rawX = anchor.x - cardWidth / 2f
        val rawY = anchor.y - cardHeight - offsetAboveDot

        val clampedX = rawX.coerceIn(0f, (parentWidth - cardWidth).coerceAtLeast(0f))
        val clampedY = if (rawY < 0f) anchor.y + offsetAboveDot else rawY
        val finalY = clampedY.coerceIn(0f, (parentHeight - cardHeight).coerceAtLeast(0f))

        card.translationX = clampedX
        card.translationY = finalY
    }

    private fun hideTowerInfoBox() {
        if (_binding == null) return
        selectedCellKey = null
        if (binding.towerInfoCard.visibility != View.GONE) {
            binding.towerInfoCard.visibility = View.GONE
        }
    }

    private fun signalDbm(m: CellMeasurement): Int = m.rsrp ?: m.rssi ?: Int.MIN_VALUE

    private fun qualityOrdinalColorExpression(): Expression =
        Expression.match(
            Expression.toNumber(Expression.get("quality_ordinal")),
            Expression.color(Color.parseColor(SignalQuality.NO_SIGNAL.colorHex)),
            Expression.stop(SignalQuality.EXCELLENT.ordinal.toLong(), Expression.color(Color.parseColor(SignalQuality.EXCELLENT.colorHex))),
            Expression.stop(SignalQuality.GOOD.ordinal.toLong(), Expression.color(Color.parseColor(SignalQuality.GOOD.colorHex))),
            Expression.stop(SignalQuality.FAIR.ordinal.toLong(), Expression.color(Color.parseColor(SignalQuality.FAIR.colorHex))),
            Expression.stop(SignalQuality.POOR.ordinal.toLong(), Expression.color(Color.parseColor(SignalQuality.POOR.colorHex))),
            Expression.stop(SignalQuality.VERY_POOR.ordinal.toLong(), Expression.color(Color.parseColor(SignalQuality.VERY_POOR.colorHex))),
            Expression.stop(SignalQuality.NO_SIGNAL.ordinal.toLong(), Expression.color(Color.parseColor(SignalQuality.NO_SIGNAL.colorHex)))
        )

    private fun radioTypeColorExpression(): Expression =
        Expression.match(
            Expression.get("radio"),
            Expression.color(Color.parseColor("#2962FF")),
            Expression.stop("LTE", Expression.color(Color.parseColor("#2962FF"))),
            Expression.stop("NR", Expression.color(Color.parseColor("#AA00FF"))),
            Expression.stop("GSM", Expression.color(Color.parseColor("#00C853"))),
            Expression.stop("WCDMA", Expression.color(Color.parseColor("#FF6D00")))
        )

    private fun updateInfoCard(measurements: List<CellMeasurement>) {
        if (!isViewAlive || _binding == null) return
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
        AppLog.d(TAG, "onResume")
        mapView?.onResume()
        mapView?.post {
            if (!isViewAlive) return@post
            val style = mapStyle ?: return@post
            enableLocationComponent(style)
            centerOnCurrentLocation()
        }
        mapViewModel.startAutoRefresh()
        startDiagnosticSampler()
    }

    override fun onPause() {
        AppLog.d(TAG, "onPause")
        stopDiagnosticSampler()
        mapViewModel.stopAutoRefresh()
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
        AppLog.d(TAG, "onDestroyView")
        stopDiagnosticSampler()
        reloadJob?.cancel()
        reloadJob = null
        lastLoadedBounds = null
        isViewAlive = false
        towerLayerInitialized = false
        locationComponentEnabled = false
        hasCenteredOnUser = false
        listenersAttached = false
        mapStyle = null
        maplibreMap = null
        mapView?.onDestroy()
        mapView = null
        _binding = null
        super.onDestroyView()
    }

}
