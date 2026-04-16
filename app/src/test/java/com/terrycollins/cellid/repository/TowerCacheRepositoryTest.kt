package com.terrycollins.cellid.repository

import com.terrycollins.cellid.data.dao.TowerCacheDao
import com.terrycollins.cellid.data.entity.TowerCacheEntity
import com.terrycollins.cellid.domain.model.CellTower
import com.terrycollins.cellid.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TowerCacheRepositoryTest {

    @Test
    fun `given a tower, when learnPosition, then dao insert is called with source learned`() = runTest {
        // Given
        val dao = mockk<TowerCacheDao>()
        val captured = slot<TowerCacheEntity>()
        every { dao.insert(capture(captured)) } just Runs
        val repo = TowerCacheRepository(dao)

        val tower = CellTower(
            radio = RadioType.LTE,
            mcc = 310,
            mnc = 260,
            tacLac = 12345,
            cid = 50331905L,
            latitude = 37.7749,
            longitude = -122.4194,
            samples = 10,
            source = "ignored-will-be-overridden"
        )

        // When
        repo.learnPosition(tower)

        // Then
        verify(exactly = 1) { dao.insert(any()) }
        assertThat(captured.captured.source).isEqualTo("learned")
        assertThat(captured.captured.latitude).isEqualTo(37.7749)
        assertThat(captured.captured.longitude).isEqualTo(-122.4194)
        assertThat(captured.captured.mcc).isEqualTo(310)
        assertThat(captured.captured.cid).isEqualTo(50331905L)
    }
}
