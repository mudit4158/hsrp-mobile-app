package com.hsrp.dealer.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A fitment or challan inward recorded on the device. It is sent by the sync worker, now or when the
 * network returns, with the same Idempotency-Key every time, so a retry never records twice.
 */
@Entity(tableName = "pending_action")
data class PendingAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val refId: String,
    val label: String,
    val idempotencyKey: String,
    val payload: String,
    val photoFront: String? = null,
    val photoRear: String? = null,
    val photoChassis: String? = null,
    val status: String = STATUS_PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_FITMENT = "FITMENT"
        const val TYPE_INWARD = "INWARD"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}

@Dao
interface PendingDao {
    @Insert
    suspend fun insert(a: PendingAction): Long

    @Update
    suspend fun update(a: PendingAction)

    @Query("SELECT * FROM pending_action ORDER BY createdAt DESC")
    fun all(): Flow<List<PendingAction>>

    @Query("SELECT * FROM pending_action WHERE status IN ('PENDING', 'SYNCING') ORDER BY createdAt")
    suspend fun due(): List<PendingAction>

    @Query("SELECT * FROM pending_action WHERE type = :type AND refId = :refId AND status != 'FAILED' LIMIT 1")
    suspend fun findActive(type: String, refId: String): PendingAction?

    @Query("SELECT COUNT(*) FROM pending_action WHERE status IN ('PENDING', 'SYNCING')")
    fun pendingCount(): Flow<Int>

    @Query("DELETE FROM pending_action WHERE status = 'DONE'")
    suspend fun clearDone()

    @Query("DELETE FROM pending_action WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [PendingAction::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun pending(): PendingDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "hsrp-dealer.db").build().also { instance = it }
        }
    }
}
