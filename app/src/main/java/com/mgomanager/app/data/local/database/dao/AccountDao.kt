package com.mgomanager.app.data.local.database.dao

import androidx.room.*
import com.mgomanager.app.data.local.database.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Account operations
 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY lastPlayedAt DESC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY lastPlayedAt DESC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId")
    suspend fun getAccountById(accountId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :accountId")
    fun getAccountByIdFlow(accountId: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE accountName = :name")
    suspend fun getAccountByName(name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE userId = :userId LIMIT 1")
    suspend fun getAccountByUserId(userId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun deleteAccountById(accountId: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    fun getAccountCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM accounts WHERE hasError = 1")
    fun getErrorAccountCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM accounts WHERE susLevelValue > 0")
    fun getSusAccountCount(): Flow<Int>

    @Query("UPDATE accounts SET lastPlayedAt = :timestamp WHERE id = :accountId")
    suspend fun updateLastPlayedTimestamp(accountId: Long, timestamp: Long)

    @Query("UPDATE accounts SET susLevelValue = :susLevel WHERE id = :accountId")
    suspend fun updateSusLevel(accountId: Long, susLevel: Int)

    @Query("UPDATE accounts SET hasError = :hasError WHERE id = :accountId")
    suspend fun updateErrorStatus(accountId: Long, hasError: Boolean)
}
