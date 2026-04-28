package com.celltowerid.android.service

import com.google.common.truth.Truth.assertThat
import com.celltowerid.android.util.Preferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class CollectionRestartPolicyTest {

    private val defaultInterval = 10_000L

    @Test
    fun `given scan not active and permission granted, when decide, then returns Stop`() {
        // Given
        val prefs = mockk<Preferences> {
            every { isScanActive } returns false
            every { scanIntervalMs } returns 0L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval, hasFineLocation = true)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Stop)
    }

    @Test
    fun `given scan active with valid interval and permission granted, when decide, then resumes with persisted interval`() {
        // Given
        val prefs = mockk<Preferences> {
            every { isScanActive } returns true
            every { scanIntervalMs } returns 12_000L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval, hasFineLocation = true)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Resume(12_000L))
    }

    @Test
    fun `given scan active with zero interval and permission granted, when decide, then resumes with default interval`() {
        // Given
        val prefs = mockk<Preferences> {
            every { isScanActive } returns true
            every { scanIntervalMs } returns 0L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval, hasFineLocation = true)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Resume(defaultInterval))
    }

    @Test
    fun `given scan active with negative interval and permission granted, when decide, then resumes with default interval`() {
        // Given -- guards against corrupt/downgraded prefs.
        val prefs = mockk<Preferences> {
            every { isScanActive } returns true
            every { scanIntervalMs } returns -1L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval, hasFineLocation = true)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Resume(defaultInterval))
    }

    @Test
    fun `given scan was active but permission revoked, when decide, then returns StopAndNotifyPermissionLost`() {
        // Given -- user revoked ACCESS_FINE_LOCATION while the process was dead.
        val prefs = mockk<Preferences> {
            every { isScanActive } returns true
            every { scanIntervalMs } returns 12_000L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval, hasFineLocation = false)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.StopAndNotifyPermissionLost)
    }

    @Test
    fun `given scan never active and no permission, when decide, then returns Stop quietly`() {
        // Given -- nothing to surface to the user; just bail.
        val prefs = mockk<Preferences> {
            every { isScanActive } returns false
            every { scanIntervalMs } returns 0L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval, hasFineLocation = false)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Stop)
    }
}
