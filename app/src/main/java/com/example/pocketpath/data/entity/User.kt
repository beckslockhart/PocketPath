package com.example.pocketpath.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a registered PocketPath user in the Room database.
 *
 * Each user receives an automatically generated ID and must have
 * a unique username.
 */
@Entity(
    tableName = "users",
    indices = [
        Index(
            value = ["username"],
            unique = true
        )
    ]
)
data class User(

    // Unique identifier automatically generated when the user is registered.
    @PrimaryKey(autoGenerate = true)
    val userId: Long = 0,

    // Username used to identify and log in the user.
    val username: String,

    // Password used for the local prototype login system.
    val password: String
)