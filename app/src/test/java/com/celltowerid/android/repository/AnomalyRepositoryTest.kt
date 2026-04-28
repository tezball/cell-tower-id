package com.celltowerid.android.repository

import com.celltowerid.android.data.dao.AnomalyDao
import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.domain.model.AnomalySeverity
import com.celltowerid.android.domain.model.AnomalyType
import com.celltowerid.android.domain.model.RadioType
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AnomalyRepositoryTest {

    private val dao: AnomalyDao = mockk(relaxed = true)
    private val repo = AnomalyRepository(dao)

    private val sample = AnomalyEvent(
        timestamp = 1_000L,
        latitude = 53.3430524,
        longitude = -6.4212192,
        type = AnomalyType.SIGNAL_ANOMALY,
        severity = AnomalySeverity.MEDIUM,
        description = "test",
        cellRadio = RadioType.LTE,
        cellMcc = 272,
        cellMnc = 5,
        cellTacLac = 41002,
        cellCid = 1_205_536L,
        cellPci = 173,
        signalStrength = -105
    )

    @Test
    fun `given matching anomaly already exists, when insertAnomaly, then dao insert is skipped`() = runBlocking {
        every {
            dao.countByCellIdentity("SIGNAL_ANOMALY", "LTE", 272, 5, 41002, 1_205_536L)
        } returns 1

        val id = repo.insertAnomaly(sample, sessionId = null)

        assertThat(id).isEqualTo(-1L)
        verify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `given no matching anomaly, when insertAnomaly, then dao insert is called`() = runBlocking {
        every {
            dao.countByCellIdentity("SIGNAL_ANOMALY", "LTE", 272, 5, 41002, 1_205_536L)
        } returns 0
        every { dao.insert(any()) } returns 42L

        val id = repo.insertAnomaly(sample, sessionId = null)

        assertThat(id).isEqualTo(42L)
        verify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `given anomaly with null cell identity, when insertAnomaly, then dao insert is called without dedupe query`() = runBlocking {
        val noCell = sample.copy(
            cellRadio = null, cellMcc = null, cellMnc = null, cellTacLac = null, cellCid = null
        )
        every { dao.insert(any()) } returns 7L

        val id = repo.insertAnomaly(noCell, sessionId = null)

        assertThat(id).isEqualTo(7L)
        verify(exactly = 0) { dao.countByCellIdentity(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `given a cutoff, when deleteOlderThan, then dao deleteOlderThan is called with that cutoff`() = runBlocking {
        every { dao.deleteOlderThan(55555L) } returns 3

        val n = repo.deleteOlderThan(55555L)

        assertThat(n).isEqualTo(3)
        verify(exactly = 1) { dao.deleteOlderThan(55555L) }
    }

    @Test
    fun `given a type, when deleteByType, then dao deleteByType is called with that type`() = runBlocking {
        every { dao.deleteByType("UNKNOWN_TOWER") } returns 12

        val n = repo.deleteByType("UNKNOWN_TOWER")

        assertThat(n).isEqualTo(12)
        verify(exactly = 1) { dao.deleteByType("UNKNOWN_TOWER") }
    }

    @Test
    fun `given an id, when dismiss, then dao dismissById is called`() = runBlocking {
        repo.dismiss(99L)
        verify(exactly = 1) { dao.dismissById(99L) }
    }

    @Test
    fun `given an id, when undismiss, then dao undismissById is called`() = runBlocking {
        repo.undismiss(77L)
        verify(exactly = 1) { dao.undismissById(77L) }
    }
}
