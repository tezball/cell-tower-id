package com.celltowerid.android.repository

import com.celltowerid.android.data.dao.SessionDao
import com.celltowerid.android.data.entity.SessionEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SessionRepositoryTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        sessionDao = mockk(relaxed = true)
        repository = SessionRepository(sessionDao)
    }

    @Test
    fun `when startSession then inserts session with description and returns id`() = runTest {
        every { sessionDao.insert(any()) } returns 42L

        val id = repository.startSession("Test session")

        assertThat(id).isEqualTo(42L)
        val captured = slot<SessionEntity>()
        verify { sessionDao.insert(capture(captured)) }
        assertThat(captured.captured.description).isEqualTo("Test session")
        assertThat(captured.captured.startTime).isGreaterThan(0L)
    }

    @Test
    fun `given no description when startSession then uses empty string`() = runTest {
        every { sessionDao.insert(any()) } returns 1L

        repository.startSession()

        val captured = slot<SessionEntity>()
        verify { sessionDao.insert(capture(captured)) }
        assertThat(captured.captured.description).isEmpty()
    }

    @Test
    fun `given existing session when endSession then updates end time and measurement count`() = runTest {
        val session = SessionEntity().apply {
            id = 5L
            startTime = 1000L
        }
        every { sessionDao.getById(5L) } returns session

        repository.endSession(5L, 100)

        val captured = slot<SessionEntity>()
        verify { sessionDao.update(capture(captured)) }
        assertThat(captured.captured.endTime).isGreaterThan(0L)
        assertThat(captured.captured.measurementCount).isEqualTo(100)
    }

    @Test
    fun `given no session found when endSession then does not update`() = runTest {
        every { sessionDao.getById(999L) } returns null

        repository.endSession(999L, 50)

        verify(exactly = 0) { sessionDao.update(any()) }
    }

    @Test
    fun `when getActiveSession then returns session with null end time`() = runTest {
        val active = SessionEntity().apply { startTime = 1000L }
        every { sessionDao.getActiveSession() } returns active

        val result = requireNotNull(repository.getActiveSession())

        assertThat(result.startTime).isEqualTo(1000L)
    }

    @Test
    fun `when getAllSessions then returns all sessions from dao`() = runTest {
        val sessions = listOf(SessionEntity(), SessionEntity(), SessionEntity())
        every { sessionDao.getAll() } returns sessions

        val result = repository.getAllSessions()

        assertThat(result).hasSize(3)
    }
}
