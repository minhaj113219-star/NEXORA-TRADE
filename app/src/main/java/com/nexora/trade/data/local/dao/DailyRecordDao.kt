package com.nexora.trade.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.nexora.trade.data.local.entity.DailyRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyRecordDao {

    @Query("SELECT * FROM daily_records WHERE dateKey = :dateKey LIMIT 1")
    fun observeRecord(dateKey: String): Flow<DailyRecordEntity?>

    @Query("SELECT * FROM daily_records WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getRecord(dateKey: String): DailyRecordEntity?

    @Query(
        "SELECT * FROM daily_records " +
            "WHERE dateKey < :dateKey AND startingBalance IS NOT NULL " +
            "ORDER BY dateKey DESC"
    )
    suspend fun getRecordsWithBalanceBefore(dateKey: String): List<DailyRecordEntity>

    @Query(
        "SELECT * FROM daily_records " +
            "WHERE dateKey BETWEEN :startDateKey AND :endDateKey " +
            "ORDER BY dateKey DESC"
    )
    fun observeRecordsInRange(
        startDateKey: String,
        endDateKey: String
    ): Flow<List<DailyRecordEntity>>

    @Upsert
    suspend fun upsert(entity: DailyRecordEntity)
}