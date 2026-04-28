package com.terrycollins.celltowerid.ui

import android.Manifest
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainActivityPermissionPolicyTest {

    // --- requiredPermissionsForSdk: cross-OS coverage ---

    @Test
    fun `given API 24 (Nougat), when requiredPermissionsForSdk, then no POST_NOTIFICATIONS`() {
        val perms = MainActivity.requiredPermissionsForSdk(Build.VERSION_CODES.N)

        assertThat(perms).containsExactly(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    @Test
    fun `given API 30 (R), when requiredPermissionsForSdk, then no POST_NOTIFICATIONS`() {
        val perms = MainActivity.requiredPermissionsForSdk(Build.VERSION_CODES.R)

        assertThat(perms).doesNotContain(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(perms).contains(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun `given API 32 (just below Tiramisu), when requiredPermissionsForSdk, then no POST_NOTIFICATIONS`() {
        val perms = MainActivity.requiredPermissionsForSdk(Build.VERSION_CODES.S_V2)

        assertThat(perms).doesNotContain(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `given API 33 (Tiramisu), when requiredPermissionsForSdk, then POST_NOTIFICATIONS is required`() {
        val perms = MainActivity.requiredPermissionsForSdk(Build.VERSION_CODES.TIRAMISU)

        assertThat(perms).contains(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(perms).contains(Manifest.permission.ACCESS_FINE_LOCATION)
        assertThat(perms).contains(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    @Test
    fun `given API 34 (Upside Down Cake), when requiredPermissionsForSdk, then POST_NOTIFICATIONS is required`() {
        val perms = MainActivity.requiredPermissionsForSdk(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        assertThat(perms).contains(Manifest.permission.POST_NOTIFICATIONS)
    }

    // --- shouldRequestBackgroundLocation: API 29+ separate-grant flow ---

    @Test
    fun `given API 28 (Pie), when shouldRequestBackgroundLocation, then returns false (no separate prompt before Q)`() {
        // On API 28 ACCESS_FINE_LOCATION covers background usage too.
        val should = MainActivity.shouldRequestBackgroundLocation(
            sdkInt = Build.VERSION_CODES.P,
            backgroundLocationGranted = false
        )
        assertThat(should).isFalse()
    }

    @Test
    fun `given API 29 (Q) and BG location not yet granted, when shouldRequestBackgroundLocation, then returns true`() {
        val should = MainActivity.shouldRequestBackgroundLocation(
            sdkInt = Build.VERSION_CODES.Q,
            backgroundLocationGranted = false
        )
        assertThat(should).isTrue()
    }

    @Test
    fun `given API 29 (Q) and BG location already granted, when shouldRequestBackgroundLocation, then returns false`() {
        val should = MainActivity.shouldRequestBackgroundLocation(
            sdkInt = Build.VERSION_CODES.Q,
            backgroundLocationGranted = true
        )
        assertThat(should).isFalse()
    }

    @Test
    fun `given API 34 and BG location not yet granted, when shouldRequestBackgroundLocation, then returns true`() {
        val should = MainActivity.shouldRequestBackgroundLocation(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            backgroundLocationGranted = false
        )
        assertThat(should).isTrue()
    }
}
