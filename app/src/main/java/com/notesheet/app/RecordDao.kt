package com.notesheet.app

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface RecordDao {

    @Insert
    suspend fun insert(record: PatientRecord): Long

    @Update
    suspend fun update(record: PatientRecord)

    @Delete
    suspend fun delete(record: PatientRecord)

    @Query("SELECT * FROM records ORDER BY id DESC")
    fun getAll(): LiveData<List<PatientRecord>>

    @Query("SELECT * FROM records ORDER BY id DESC")
    suspend fun getAllSync(): List<PatientRecord>

    @Query("""
        SELECT * FROM records
        WHERE bedNumber LIKE '%' || :query || '%'
           OR patientName LIKE '%' || :query || '%'
           OR primaryConsultant LIKE '%' || :query || '%'
           OR details LIKE '%' || :query || '%'
        ORDER BY id DESC
    """)
    fun search(query: String): LiveData<List<PatientRecord>>
}
