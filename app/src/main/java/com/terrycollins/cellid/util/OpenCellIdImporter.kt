package com.terrycollins.cellid.util

import android.content.Context
import android.util.Log
import com.terrycollins.cellid.repository.TowerCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object OpenCellIdImporter {

    private const val TAG = "CellID.OpenCellIdImporter"
    private const val ASSET_PATH = "opencellid/272.csv"
    private const val BATCH_SIZE = 1000

    suspend fun importIfEmpty(context: Context, repo: TowerCacheRepository): Int {
        return withContext(Dispatchers.IO) {
            val existing = repo.getTowerCount()
            if (existing > 0) {
                Log.d(TAG, "Tower cache already populated ($existing rows); skipping import")
                return@withContext 0
            }
            importFromAsset(context, repo)
        }
    }

    private suspend fun importFromAsset(context: Context, repo: TowerCacheRepository): Int {
        var imported = 0
        val batch = ArrayList<com.terrycollins.cellid.domain.model.CellTower>(BATCH_SIZE)

        context.assets.open(ASSET_PATH).use { input ->
            BufferedReader(InputStreamReader(input)).useLines { lines ->
                for (line in lines) {
                    val tower = OpenCellIdCsvParser.parseLine(line) ?: continue
                    batch.add(tower)
                    if (batch.size >= BATCH_SIZE) {
                        repo.addTowers(batch)
                        imported += batch.size
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) {
                    repo.addTowers(batch)
                    imported += batch.size
                }
            }
        }
        Log.i(TAG, "Imported $imported towers from $ASSET_PATH")
        return imported
    }
}
