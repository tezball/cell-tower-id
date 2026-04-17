package com.terrycollins.celltowerid.service

import com.google.common.truth.Truth.assertThat
import com.terrycollins.celltowerid.util.Preferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class CollectionRestartPolicyTest {

    private val defaultInterval = 5_000L

    @Test
    fun `given scan not active, when decide, then returns Stop`() {
        // Given
        val prefs = mockk<Preferences> {
            every { isScanActive } returns false
            every { scanIntervalMs } returns 0L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Stop)
    }

    @Test
    fun `given scan active with valid interval, when decide, then returns Resume with persisted interval`() {
        // Given
        val prefs = mockk<Preferences> {
            every { isScanActive } returns true
            every { scanIntervalMs } returns 12_000L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Resume(12_000L))
    }

    @Test
    fun `given scan active with zero interval, when decide, then resumes with default interval`() {
        // Given
        val prefs = mockk<Preferences> {
            every { isScanActive } returns true
            every { scanIntervalMs } returns 0L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Resume(defaultInterval))
    }

    @Test
    fun `given scan active with negative interval, when decide, then resumes with default interval`() {
        // Given — guards against corrupt/downgraded prefs.
        val prefs = mockk<Preferences> {
            every { isScanActive } returns true
            every { scanIntervalMs } returns -1L
        }

        // When
        val decision = CollectionRestartPolicy.decide(prefs, defaultInterval)

        // Then
        assertThat(decision).isEqualTo(CollectionRestartPolicy.Decision.Resume(defaultInterval))
    }
}
