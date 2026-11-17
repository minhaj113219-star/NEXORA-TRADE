package com.nexora.trade.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nexora.trade.data.local.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    @Query(
        "SELECT * FROM trades WHERE dateKey = :dateKey " +
            "ORDER BY timestampMillis DESC, id DESC"
    )
    fun observeTradesForDate(dateKey: String): Flow<List<TradeEntity>>

    @Query(
        "SELECT * FROM trades WHERE dateKey = :dateKey " +
            "ORDER BY timestampMillis DESC, id DESC"
    )
    suspend fun getTradesForDate(dateKey: String): List<TradeEntity>

    @Query(
        "SELECT * FROM trades " +
            "WHERE dateKey BETWEEN :startDateKey AND :endDateKey " +
            "ORDER BY dateKey DESC, timestampMillis DESC, id DESC"
    )
    fun observeTradesInRange(
        startDateKey: String,
        endDateKey: String
    ): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades ORDER BY dateKey DESC, timestampMillis DESC, id DESC")
    fun observeAllTrades(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE id = :tradeId LIMIT 1")
    suspend fun getTradeById(tradeId: Long): TradeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(trade: TradeEntity): Long

    @Update
    suspend fun update(trade: TradeEntity)

    @Query("DELETE FROM trades WHERE id = :tradeId")
    suspend fun deleteById(tradeId: Long)
}