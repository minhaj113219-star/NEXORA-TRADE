package com.nexora.trade.data.repository

import androidx.room.withTransaction
import com.nexora.trade.data.local.NexoraDatabase
import com.nexora.trade.data.local.dao.DailyRecordDao
import com.nexora.trade.data.local.dao.TradeDao
import com.nexora.trade.data.local.entity.DailyRecordEntity
import com.nexora.trade.data.local.entity.TradeEntity
import com.nexora.trade.domain.calculator.AlertEvaluator
import com.nexora.trade.domain.calculator.MoneyCalculator
import com.nexora.trade.domain.calculator.ThirtyDayAggregator
import com.nexora.trade.domain.model.AlertEvaluationResult
import com.nexora.trade.domain.model.DailyCalculationSummary
import com.nexora.trade.domain.model.DailyRecord
import com.nexora.trade.domain.model.ThirtyDaySummary
import com.nexora.trade.domain.model.TradeEntry
import com.nexora.trade.domain.model.TradeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate

data class DaySnapshot(
    val date: LocalDate,
    val record: DailyRecord?,
    val trades: List<TradeEntry>,
    val summary: DailyCalculationSummary
)

interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: NexoraDatabase
) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.withTransaction { block() }
    }
}

class ImmediateTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return block()
    }
}

interface TradingRepository {
    fun observeDaySnapshot(date: LocalDate): Flow<DaySnapshot>
    fun observeThirtyDaySummary(endDate: LocalDate): Flow<ThirtyDaySummary>
    fun observeAllTrades(): Flow<List<TradeEntry>>
    fun observeDailyRecord(date: LocalDate): Flow<DailyRecord?>
    suspend fun getTradeById(tradeId: Long): TradeEntry?

    suspend fun ensureCarryOverStartingBalance(date: LocalDate): AlertEvaluationResult?

    suspend fun setStartingBalance(
        date: LocalDate,
        startingBalance: BigDecimal
    ): AlertEvaluationResult

    suspend fun addTrade(
        date: LocalDate,
        type: TradeType,
        amount: BigDecimal,
        note: String,
        timestampMillis: Long = System.currentTimeMillis(),
        stakeAmount: BigDecimal? = null,
        payoutPercent: BigDecimal? = null
    ): AlertEvaluationResult

    suspend fun updateTrade(
        tradeId: Long,
        type: TradeType,
        amount: BigDecimal,
        note: String,
        stakeAmount: BigDecimal? = null,
        payoutPercent: BigDecimal? = null
    ): AlertEvaluationResult

    suspend fun deleteTrade(tradeId: Long)

    suspend fun saveDailyReflection(date: LocalDate, reflectionText: String)
}

class RoomTradingRepository(
    private val dailyRecordDao: DailyRecordDao,
    private val tradeDao: TradeDao,
    private val transactionRunner: TransactionRunner,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : TradingRepository {

    override fun observeDaySnapshot(date: LocalDate): Flow<DaySnapshot> {
        val dateKey = date.toString()
        return combine(
            dailyRecordDao.observeRecord(dateKey),
            tradeDao.observeTradesForDate(dateKey)
        ) { recordEntity, tradeEntities ->
            val record = recordEntity?.toDomain()
            val trades = tradeEntities.map { it.toDomain() }
            val summary = MoneyCalculator.calculateDailySummary(
                date = date,
                startingBalance = record?.startingBalance,
                trades = trades
            )
            DaySnapshot(
                date = date,
                record = record,
                trades = trades,
                summary = summary
            )
        }
    }

    override fun observeThirtyDaySummary(endDate: LocalDate): Flow<ThirtyDaySummary> {
        val startDate = endDate.minusDays(ThirtyDayAggregator.WINDOW_DAYS - 1L)
        val startKey = startDate.toString()
        val endKey = endDate.toString()

        return combine(
            dailyRecordDao.observeRecordsInRange(startKey, endKey),
            tradeDao.observeTradesInRange(startKey, endKey)
        ) { recordEntities, tradeEntities ->
            val recordsByDate = recordEntities
                .map { it.toDomain() }
                .associateBy { it.date }
            val tradesByDate = tradeEntities
                .map { it.toDomain() }
                .groupBy { it.date }

            ThirtyDayAggregator.buildSummary(
                endDate = endDate,
                recordsByDate = recordsByDate,
                tradesByDate = tradesByDate
            )
        }
    }

    override fun observeAllTrades(): Flow<List<TradeEntry>> {
        return tradeDao.observeAllTrades().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeDailyRecord(date: LocalDate): Flow<DailyRecord?> {
        return dailyRecordDao.observeRecord(date.toString()).map { it?.toDomain() }
    }

    override suspend fun getTradeById(tradeId: Long): TradeEntry? {
        return tradeDao.getTradeById(tradeId)?.toDomain()
    }

    override suspend fun ensureCarryOverStartingBalance(date: LocalDate): AlertEvaluationResult? {
        val dateKey = date.toString()
        return transactionRunner.runInTransaction {
            val existingToday = dailyRecordDao.getRecord(dateKey)
            val hasManualBalance = existingToday?.startingBalance != null &&
                existingToday.startingBalance > BigDecimal.ZERO &&
                !existingToday.isAutoCarriedOver

            if (hasManualBalance) {
                return@runInTransaction null
            }

            val latestPrevRecord = dailyRecordDao.getRecordsWithBalanceBefore(dateKey)
                .firstOrNull { it.startingBalance != null && it.startingBalance > BigDecimal.ZERO }
                ?: return@runInTransaction null

            val prevDate = LocalDate.parse(latestPrevRecord.dateKey)
            val prevTrades = tradeDao.getTradesForDate(latestPrevRecord.dateKey).map { it.toDomain() }
            val prevSummary = MoneyCalculator.calculateDailySummary(
                date = prevDate,
                startingBalance = latestPrevRecord.startingBalance,
                trades = prevTrades
            )

            val carryOverBalance = prevSummary.currentBalance
            if (carryOverBalance <= BigDecimal.ZERO) {
                return@runInTransaction null
            }

            val normalized = MoneyCalculator.normalizeCurrency(carryOverBalance)
            if (existingToday?.startingBalance != null &&
                existingToday.startingBalance.compareTo(normalized) == 0 &&
                existingToday.isAutoCarriedOver
            ) {
                return@runInTransaction null
            }

            val balanceChanged = existingToday?.startingBalance?.compareTo(normalized) != 0
            val baselineRecord = DailyRecord(
                date = date,
                startingBalance = normalized,
                targetAlertTriggered = if (balanceChanged) false else (existingToday?.targetAlertTriggered ?: false),
                lossAlertTriggered = if (balanceChanged) false else (existingToday?.lossAlertTriggered ?: false),
                reflectionText = existingToday?.reflectionText.orEmpty(),
                updatedAtMillis = timeProvider(),
                isAutoCarriedOver = true
            )

            val tradesToday = tradeDao.getTradesForDate(dateKey).map { it.toDomain() }
            val summaryToday = MoneyCalculator.calculateDailySummary(
                date = date,
                startingBalance = normalized,
                trades = tradesToday
            )
            val evaluation = AlertEvaluator.evaluate(summaryToday, baselineRecord)

            dailyRecordDao.upsert(
                DailyRecordEntity(
                    dateKey = dateKey,
                    startingBalance = normalized,
                    targetAlertTriggered = evaluation.updatedTargetAlertTriggered,
                    lossAlertTriggered = evaluation.updatedLossAlertTriggered,
                    reflectionText = baselineRecord.reflectionText,
                    updatedAtMillis = timeProvider(),
                    isAutoCarriedOver = true
                )
            )

            evaluation
        }
    }

    override suspend fun setStartingBalance(
        date: LocalDate,
        startingBalance: BigDecimal
    ): AlertEvaluationResult {
        require(startingBalance > BigDecimal.ZERO) {
            "Starting balance must be greater than zero."
        }
        val normalized = MoneyCalculator.normalizeCurrency(startingBalance)
        val dateKey = date.toString()

        return transactionRunner.runInTransaction {
            val existing = dailyRecordDao.getRecord(dateKey)
            val balanceChanged = existing?.startingBalance?.compareTo(normalized) != 0

            val baselineRecord = DailyRecord(
                date = date,
                startingBalance = normalized,
                targetAlertTriggered = if (balanceChanged) false else (existing?.targetAlertTriggered ?: false),
                lossAlertTriggered = if (balanceChanged) false else (existing?.lossAlertTriggered ?: false),
                reflectionText = existing?.reflectionText.orEmpty(),
                updatedAtMillis = timeProvider(),
                isAutoCarriedOver = false
            )

            val trades = tradeDao.getTradesForDate(dateKey).map { it.toDomain() }
            val summary = MoneyCalculator.calculateDailySummary(
                date = date,
                startingBalance = normalized,
                trades = trades
            )
            val evaluation = AlertEvaluator.evaluate(summary, baselineRecord)

            dailyRecordDao.upsert(
                DailyRecordEntity(
                    dateKey = dateKey,
                    startingBalance = normalized,
                    targetAlertTriggered = evaluation.updatedTargetAlertTriggered,
                    lossAlertTriggered = evaluation.updatedLossAlertTriggered,
                    reflectionText = baselineRecord.reflectionText,
                    updatedAtMillis = timeProvider(),
                    isAutoCarriedOver = false
                )
            )

            evaluation
        }
    }

    override suspend fun addTrade(
        date: LocalDate,
        type: TradeType,
        amount: BigDecimal,
        note: String,
        timestampMillis: Long,
        stakeAmount: BigDecimal?,
        payoutPercent: BigDecimal?
    ): AlertEvaluationResult {
        require(amount > BigDecimal.ZERO) {
            "Trade amount must be greater than zero."
        }
        val normalizedAmount = MoneyCalculator.normalizeCurrency(amount)
        val normalizedStake = stakeAmount?.let { MoneyCalculator.normalizeCurrency(it) }
        val normalizedPayout = payoutPercent?.let { MoneyCalculator.normalizeCurrency(it) }
        val dateKey = date.toString()

        return transactionRunner.runInTransaction {
            tradeDao.insert(
                TradeEntity(
                    dateKey = dateKey,
                    type = type,
                    amount = normalizedAmount,
                    stakeAmount = normalizedStake,
                    payoutPercent = normalizedPayout,
                    note = note.trim(),
                    timestampMillis = timestampMillis
                )
            )
            evaluateAndPersistAlertsForDate(date)
        }
    }

    override suspend fun updateTrade(
        tradeId: Long,
        type: TradeType,
        amount: BigDecimal,
        note: String,
        stakeAmount: BigDecimal?,
        payoutPercent: BigDecimal?
    ): AlertEvaluationResult {
        require(amount > BigDecimal.ZERO) {
            "Trade amount must be greater than zero."
        }
        val normalizedAmount = MoneyCalculator.normalizeCurrency(amount)
        val normalizedStake = stakeAmount?.let { MoneyCalculator.normalizeCurrency(it) }
        val normalizedPayout = payoutPercent?.let { MoneyCalculator.normalizeCurrency(it) }

        return transactionRunner.runInTransaction {
            val existing = tradeDao.getTradeById(tradeId)
                ?: throw IllegalArgumentException("Trade not found for id=$tradeId")

            tradeDao.update(
                existing.copy(
                    type = type,
                    amount = normalizedAmount,
                    stakeAmount = normalizedStake,
                    payoutPercent = normalizedPayout,
                    note = note.trim()
                )
            )

            evaluateAndPersistAlertsForDate(LocalDate.parse(existing.dateKey))
        }
    }

    override suspend fun deleteTrade(tradeId: Long) {
        transactionRunner.runInTransaction {
            tradeDao.deleteById(tradeId)
        }
    }

    override suspend fun saveDailyReflection(date: LocalDate, reflectionText: String) {
        val dateKey = date.toString()
        val now = timeProvider()

        transactionRunner.runInTransaction {
            val existing = dailyRecordDao.getRecord(dateKey)
            val updated = existing?.copy(
                reflectionText = reflectionText,
                updatedAtMillis = now
            ) ?: DailyRecordEntity(
                dateKey = dateKey,
                startingBalance = null,
                targetAlertTriggered = false,
                lossAlertTriggered = false,
                reflectionText = reflectionText,
                updatedAtMillis = now,
                isAutoCarriedOver = false
            )
            dailyRecordDao.upsert(updated)
        }
    }

    private suspend fun evaluateAndPersistAlertsForDate(date: LocalDate): AlertEvaluationResult {
        val dateKey = date.toString()
        val existingEntity = dailyRecordDao.getRecord(dateKey)
        val existingDomain = existingEntity?.toDomain()
        val trades = tradeDao.getTradesForDate(dateKey).map { it.toDomain() }

        val summary = MoneyCalculator.calculateDailySummary(
            date = date,
            startingBalance = existingDomain?.startingBalance,
            trades = trades
        )
        val evaluation = AlertEvaluator.evaluate(summary, existingDomain)

        if (existingEntity != null &&
            (existingEntity.targetAlertTriggered != evaluation.updatedTargetAlertTriggered ||
                existingEntity.lossAlertTriggered != evaluation.updatedLossAlertTriggered)
        ) {
            dailyRecordDao.upsert(
                existingEntity.copy(
                    targetAlertTriggered = evaluation.updatedTargetAlertTriggered,
                    lossAlertTriggered = evaluation.updatedLossAlertTriggered,
                    updatedAtMillis = timeProvider()
                )
            )
        }

        return evaluation
    }
}