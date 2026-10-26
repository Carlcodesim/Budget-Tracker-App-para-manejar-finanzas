package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para los gastos recurrentes y suscripciones.
 */
@Dao
interface RecurringExpenseDao {

    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth ASC, title ASC")
    fun getAllRecurringExpenses(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE isEnabled = 1 ORDER BY dayOfMonth ASC")
    fun getActiveRecurringExpenses(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE isEnabled = 1")
    suspend fun getActiveRecurringExpensesSync(): List<RecurringExpenseEntity>

    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth ASC")
    suspend fun getAllRecurringExpensesSync(): List<RecurringExpenseEntity>

    @Query("SELECT * FROM recurring_expenses WHERE id = :id")
    suspend fun getRecurringExpenseById(id: Long): RecurringExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringExpense(expense: RecurringExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringExpenses(expenses: List<RecurringExpenseEntity>)

    @Update
    suspend fun updateRecurringExpense(expense: RecurringExpenseEntity)

    @Delete
    suspend fun deleteRecurringExpense(expense: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteRecurringExpenseById(id: Long)

    @Query("UPDATE recurring_expenses SET lastExecutedMonth = :yearMonth WHERE id = :id")
    suspend fun updateLastExecutedMonth(id: Long, yearMonth: String)

    @Query("UPDATE recurring_expenses SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateEnabledStatus(id: Long, isEnabled: Boolean)
}
