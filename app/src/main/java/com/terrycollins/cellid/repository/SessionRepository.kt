package com.terrycollins.cellid.repository

import com.terrycollins.cellid.data.dao.SessionDao
import com.terrycollins.cellid.data.entity.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionRepository(private val sessionDao: SessionDao) {

    suspend fun startSession(description: String? = null): Long {
        return withContext(Dispatchers.IO) {
            val session = SessionEntity()
            session.startTime = System.currentTimeMillis()
            session.description = description ?: ""
            sessionDao.insert(session)
        }
    }

    suspend fun endSession(sessionId: Long, measurementCount: Int) {
        withContext(Dispatchers.IO) {
            val session = sessionDao.getById(sessionId) ?: return@withContext
            session.endTime = System.currentTimeMillis()
            session.measurementCount = measurementCount
            sessionDao.update(session)
        }
    }

    suspend fun getActiveSession(): SessionEntity? {
        return withContext(Dispatchers.IO) { sessionDao.getActiveSession() }
    }

    suspend fun getAllSessions(): List<SessionEntity> {
        return withContext(Dispatchers.IO) { sessionDao.getAll() }
    }
}
