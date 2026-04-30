package com.celltowerid.android.util

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesTest {

    private lateinit var prefs: Preferences

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        prefs = Preferences(context)
        // Reset to defaults for test isolation.
        prefs.isScanActive = false
        prefs.scanIntervalMs = 0L
        prefs.backupLocationUri = null
    }

    @Test
    fun `given fresh preferences, when isScanActive read, then defaults to false`() {
        assertThat(prefs.isScanActive).isFalse()
    }

    @Test
    fun `given fresh preferences, when scanIntervalMs read, then defaults to zero`() {
        assertThat(prefs.scanIntervalMs).isEqualTo(0L)
    }

    @Test
    fun `given scan state persisted, when read back, then returns same values`() {
        // Given
        prefs.isScanActive = true
        prefs.scanIntervalMs = 7_500L

        // When
        val active = prefs.isScanActive
        val interval = prefs.scanIntervalMs

        // Then
        assertThat(active).isTrue()
        assertThat(interval).isEqualTo(7_500L)
    }

    @Test
    fun `given scan state persisted, when read via new Preferences instance, then survives`() {
        // Given
        prefs.isScanActive = true
        prefs.scanIntervalMs = 10_000L

        // When
        val other = Preferences(RuntimeEnvironment.getApplication())

        // Then
        assertThat(other.isScanActive).isTrue()
        assertThat(other.scanIntervalMs).isEqualTo(10_000L)
    }

    @Test
    fun `given scan marked active then cleared, when read, then reflects cleared state`() {
        // Given
        prefs.isScanActive = true
        prefs.scanIntervalMs = 5_000L

        // When
        prefs.isScanActive = false
        prefs.scanIntervalMs = 0L

        // Then
        assertThat(prefs.isScanActive).isFalse()
        assertThat(prefs.scanIntervalMs).isEqualTo(0L)
    }

    @Test
    fun `given fresh preferences, when backupLocationUri read, then is null`() {
        assertThat(prefs.backupLocationUri).isNull()
    }

    @Test
    fun `given backupLocationUri set, when read back, then returns same value`() {
        // Given
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FCellID"

        // When
        prefs.backupLocationUri = uri

        // Then
        assertThat(prefs.backupLocationUri).isEqualTo(uri)
    }

    @Test
    fun `given backupLocationUri set then cleared with null, when read, then is null`() {
        // Given
        prefs.backupLocationUri = "content://example/tree/abc"

        // When
        prefs.backupLocationUri = null

        // Then
        assertThat(prefs.backupLocationUri).isNull()
    }

    @Test
    fun `given backupLocationUri set, when read via new Preferences instance, then survives`() {
        // Given
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
        prefs.backupLocationUri = uri

        // When
        val other = Preferences(RuntimeEnvironment.getApplication())

        // Then
        assertThat(other.backupLocationUri).isEqualTo(uri)
    }
}
