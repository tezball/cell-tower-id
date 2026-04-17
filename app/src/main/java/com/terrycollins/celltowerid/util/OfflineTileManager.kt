package com.terrycollins.celltowerid.util

import android.content.Context
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

object OfflineTileManager {

    private const val TAG = "CellTowerID.Offline"
    private const val MIN_ZOOM = 10.0
    private const val MAX_ZOOM = 16.0
    private const val MAX_REGIONS = 5
    private const val METADATA_PREFIX = "celltowerid_"

    fun cacheVisibleRegion(context: Context, bounds: LatLngBounds, styleUrl: String) {
        // Skip caching if bounds are too large (e.g. world view before camera centers)
        val latSpan = bounds.latitudeNorth - bounds.latitudeSouth
        val lonSpan = bounds.longitudeEast - bounds.longitudeWest
        if (latSpan > 1.0 || lonSpan > 1.0) {
            AppLog.d(TAG, "Skipping cache: bounds too large (${latSpan}x${lonSpan})")
            return
        }

        val offlineManager = OfflineManager.getInstance(context)
        val regionKey = boundsKey(bounds)

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val existing = offlineRegions ?: emptyArray()

                // Check if this region is already cached
                val alreadyCached = existing.any { region ->
                    metadataKey(region) == regionKey
                }
                if (alreadyCached) {
                    AppLog.d(TAG, "Region already cached: $regionKey")
                    return
                }

                // Evict oldest if at limit
                val ourRegions = existing.filter { metadataKey(it)?.startsWith(METADATA_PREFIX) == true }
                if (ourRegions.size >= MAX_REGIONS) {
                    ourRegions.firstOrNull()?.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            AppLog.d(TAG, "Evicted oldest cached region")
                        }
                        override fun onError(error: String) {
                            AppLog.e(TAG, "Failed to evict region: $error")
                        }
                    })
                }

                // Create new offline region
                val definition = OfflineTilePyramidRegionDefinition(
                    styleUrl,
                    bounds,
                    MIN_ZOOM,
                    MAX_ZOOM,
                    context.resources.displayMetrics.density
                )
                val metadata = regionKey.toByteArray(Charsets.UTF_8)

                offlineManager.createOfflineRegion(
                    definition,
                    metadata,
                    object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(region: OfflineRegion) {
                            AppLog.d(TAG, "Started caching region: $regionKey")
                            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                                override fun onStatusChanged(status: org.maplibre.android.offline.OfflineRegionStatus) {
                                    if (status.isComplete) {
                                        AppLog.d(TAG, "Region cached: $regionKey (${status.completedResourceCount} tiles)")
                                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                    }
                                }
                                override fun onError(error: org.maplibre.android.offline.OfflineRegionError) {
                                    AppLog.e(TAG, "Tile cache error: ${error.reason}: ${error.message}")
                                }
                                override fun mapboxTileCountLimitExceeded(limit: Long) {
                                    AppLog.w(TAG, "Tile count limit exceeded: $limit")
                                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                }
                            })
                        }
                        override fun onError(error: String) {
                            AppLog.e(TAG, "Failed to create offline region: $error")
                        }
                    }
                )
            }

            override fun onError(error: String) {
                AppLog.e(TAG, "Failed to list offline regions: $error")
            }
        })
    }

    fun clearCache(context: Context) {
        val offlineManager = OfflineManager.getInstance(context)
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions?.filter { metadataKey(it)?.startsWith(METADATA_PREFIX) == true }
                    ?.forEach { region ->
                        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {
                                AppLog.d(TAG, "Deleted cached region")
                            }
                            override fun onError(error: String) {
                                AppLog.e(TAG, "Failed to delete region: $error")
                            }
                        })
                    }
            }
            override fun onError(error: String) {
                AppLog.e(TAG, "Failed to list regions for clearing: $error")
            }
        })
    }

    private fun boundsKey(bounds: LatLngBounds): String {
        return "${METADATA_PREFIX}%.4f_%.4f_%.4f_%.4f".format(
            bounds.latitudeSouth, bounds.longitudeWest,
            bounds.latitudeNorth, bounds.longitudeEast
        )
    }

    private fun metadataKey(region: OfflineRegion): String? {
        return try {
            String(region.metadata, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
