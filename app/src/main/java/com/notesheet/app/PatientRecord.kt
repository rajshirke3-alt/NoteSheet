package com.notesheet.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single row in the sheet.
 * dateAdded is always stamped with the actual system date/time at the moment
 * of saving - it is NEVER derived from the order in which the user spoke
 * the fields during voice entry.
 */
@Entity(tableName = "records")
data class PatientRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bedNumber: String,
    val patientName: String,
    val primaryConsultant: String,
    val details: String,
    val dateAdded: String
)
