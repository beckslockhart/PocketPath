package com.example.pocketpath.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pocketpath.data.entity.User

/**
 * Contains the Room database operations used for registration,
 * login and retrieving user information.
 */
@Dao
interface UserDao {

    /**
     * Inserts a new user and returns their automatically generated ID.
     *
     * ABORT prevents the insertion if the unique username already exists.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: User): Long

    /**
     * Searches for an existing account with the supplied username.
     * This is used to prevent duplicate usernames during registration.
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    /**
     * Returns the user whose username and password match the entered
     * login details, or null when the credentials are incorrect.
     */
    @Query(
        """
        SELECT * FROM users
        WHERE username = :username AND password = :password
        LIMIT 1
        """
    )
    suspend fun login(username: String, password: String): User?

    /**
     * Retrieves a user from their ID so the Dashboard can display
     * information belonging to the active account.
     */
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?
}