package com.celltowerid.android.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OfflineTileManagerTest {

    // --- isCacheable: bounds size guard ---

    @Test
    fun `given a typical city-scale viewport about 5km wide, when isCacheable, then returns true`() {
        // ~5km lat span, ~5km lon span at SF latitude.
        val cacheable = OfflineTileManager.isCacheable(
            latSouth = 37.770, lonWest = -122.430,
            latNorth = 37.815, lonEast = -122.380
        )
        assertThat(cacheable).isTrue()
    }

    @Test
    fun `given a continent-spanning viewport, when isCacheable, then returns false`() {
        val cacheable = OfflineTileManager.isCacheable(
            latSouth = 25.0, lonWest = -125.0,
            latNorth = 49.0, lonEast = -66.0
        )
        assertThat(cacheable).isFalse()
    }

    @Test
    fun `given lat span just at 1 degree, when isCacheable, then returns true (inclusive boundary)`() {
        val cacheable = OfflineTileManager.isCacheable(
            latSouth = 37.0, lonWest = -122.0,
            latNorth = 38.0, lonEast = -121.5
        )
        assertThat(cacheable).isTrue()
    }

    @Test
    fun `given lat span just over 1 degree, when isCacheable, then returns false`() {
        val cacheable = OfflineTileManager.isCacheable(
            latSouth = 37.0, lonWest = -122.0,
            latNorth = 38.001, lonEast = -121.5
        )
        assertThat(cacheable).isFalse()
    }

    @Test
    fun `given lon span just over 1 degree, when isCacheable, then returns false`() {
        val cacheable = OfflineTileManager.isCacheable(
            latSouth = 37.0, lonWest = -122.0,
            latNorth = 37.5, lonEast = -120.999
        )
        assertThat(cacheable).isFalse()
    }

    // --- boundsKey: deterministic, prefixed, 4-decimal precision ---

    @Test
    fun `given a viewport, when boundsKey, then result starts with prefix and has all four corners`() {
        val key = OfflineTileManager.boundsKey(
            latSouth = 37.7700, lonWest = -122.4300,
            latNorth = 37.8150, lonEast = -122.3800
        )
        assertThat(key).startsWith(OfflineTileManager.METADATA_PREFIX)
        assertThat(key).contains("37.7700")
        assertThat(key).contains("-122.4300")
        assertThat(key).contains("37.8150")
        assertThat(key).contains("-122.3800")
    }

    @Test
    fun `given two calls with the same bounds, when boundsKey, then keys match exactly (deterministic)`() {
        val a = OfflineTileManager.boundsKey(37.77, -122.43, 37.815, -122.38)
        val b = OfflineTileManager.boundsKey(37.77, -122.43, 37.815, -122.38)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `given two slightly different viewports, when boundsKey, then keys differ`() {
        val a = OfflineTileManager.boundsKey(37.770, -122.430, 37.815, -122.380)
        val b = OfflineTileManager.boundsKey(37.771, -122.430, 37.815, -122.380)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `given coordinate beyond 4 decimals, when boundsKey, then it is rounded to 4 decimals`() {
        val key = OfflineTileManager.boundsKey(
            latSouth = 37.77001, lonWest = -122.43001,
            latNorth = 37.81502, lonEast = -122.38003
        )
        // 37.77001 rounds to 37.7700; verify trailing precision is dropped.
        assertThat(key).contains("37.7700")
        assertThat(key).doesNotContain("37.77001")
    }
}
