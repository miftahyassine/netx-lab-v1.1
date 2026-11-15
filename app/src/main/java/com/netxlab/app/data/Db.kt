package com.netxlab.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val target: String,
    val summary: String,
    val data: String = "",
    val projectId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_hosts")
data class SavedHost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val alias: String = "",
    val note: String = "",
    val favorite: Boolean = false,
    val authorized: Boolean = false,
    val scope: String = "",
    val projectId: Long? = null
)

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val created: Long = System.currentTimeMillis()
)

@Dao
interface NxDao {
    @Insert suspend fun insertRecord(r: ScanRecord): Long
    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC") fun records(): Flow<List<ScanRecord>>
    @Query("DELETE FROM scan_records") suspend fun clearRecords()
    @Delete suspend fun deleteRecord(r: ScanRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHost(h: SavedHost): Long
    @Query("SELECT * FROM saved_hosts ORDER BY favorite DESC, alias, address") fun hosts(): Flow<List<SavedHost>>
    @Delete suspend fun deleteHost(h: SavedHost)
    @Query("DELETE FROM saved_hosts") suspend fun clearHosts()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProject(p: Project): Long
    @Query("SELECT * FROM projects ORDER BY created DESC") fun projects(): Flow<List<Project>>
    @Delete suspend fun deleteProject(p: Project)
    @Query("DELETE FROM projects") suspend fun clearProjects()

    @Query("SELECT * FROM scan_records") suspend fun allRecords(): List<ScanRecord>
    @Query("SELECT * FROM saved_hosts") suspend fun allHosts(): List<SavedHost>
    @Query("SELECT * FROM projects") suspend fun allProjects(): List<Project>
}

@Database(entities = [ScanRecord::class, SavedHost::class, Project::class], version = 1, exportSchema = false)
abstract class NxDb : RoomDatabase() {
    abstract fun dao(): NxDao
}
