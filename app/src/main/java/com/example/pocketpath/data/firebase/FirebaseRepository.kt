package com.example.pocketpath.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object FirebaseRepository {

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val currentUserId: String?
        get() = auth.currentUser?.uid

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)

    suspend fun register(email: String, password: String, username: String): String {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Registration failed")
        userDoc(uid).set(mapOf("username" to username, "email" to email)).await()
        return uid
    }

    suspend fun registerWithUsername(username: String, email: String, password: String): String {
        val usernameKey = username.lowercase()
        val existing = firestore.collection("usernameIndex").document(usernameKey).get().await()

        if (existing.exists()) {
            error("Username already taken")
        }

        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Registration failed")

        userDoc(uid).set(mapOf("username" to username, "email" to email)).await()

        firestore.collection("usernameIndex").document(usernameKey)
            .set(mapOf("email" to email, "uid" to uid))
            .await()

        return uid
    }

    suspend fun resolveEmailForLogin(input: String): String {
        if (input.contains("@")) {
            return input
        }

        val snapshot = firestore.collection("usernameIndex").document(input.lowercase()).get().await()
        return snapshot.getString("email") ?: error("No account found for that username")
    }

    suspend fun login(email: String, password: String): String {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user?.uid ?: error("Login failed")
    }

    fun logout() = auth.signOut()

    suspend fun getUsername(uid: String): String {
        val snapshot = userDoc(uid).get().await()
        return snapshot.getString("username") ?: "there"
    }

    data class GoalData(val minimumAmount: Double = 0.0, val maximumAmount: Double = 0.0)

    fun observeMonthlyGoal(uid: String): Flow<GoalData?> = callbackFlow {
        val registration = userDoc(uid).collection("meta").document("monthlyGoal")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        GoalData(
                            minimumAmount = snapshot.getDouble("minimumAmount") ?: 0.0,
                            maximumAmount = snapshot.getDouble("maximumAmount") ?: 0.0
                        )
                    )
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveMonthlyGoal(uid: String, minimum: Double, maximum: Double) {
        userDoc(uid).collection("meta").document("monthlyGoal")
            .set(mapOf("minimumAmount" to minimum, "maximumAmount" to maximum))
            .await()
    }

    data class CategoryData(
        val categoryId: String = "",
        val name: String = "",
        val monthlyLimit: Double = 0.0
    )

    fun observeCategories(uid: String): Flow<List<CategoryData>> = callbackFlow {
        val registration = userDoc(uid).collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents?.map { doc ->
                    CategoryData(
                        categoryId = doc.id,
                        name = doc.getString("name") ?: "",
                        monthlyLimit = doc.getDouble("monthlyLimit") ?: 0.0
                    )
                } ?: emptyList()
                trySend(categories)
            }
        awaitClose { registration.remove() }
    }

    suspend fun addCategory(uid: String, name: String, monthlyLimit: Double) {
        userDoc(uid).collection("categories")
            .add(mapOf("name" to name, "monthlyLimit" to monthlyLimit))
            .await()
    }

    suspend fun updateCategory(uid: String, categoryId: String, name: String, monthlyLimit: Double) {
        userDoc(uid).collection("categories").document(categoryId)
            .set(mapOf("name" to name, "monthlyLimit" to monthlyLimit))
            .await()
    }

    suspend fun deleteCategory(uid: String, categoryId: String) {
        userDoc(uid).collection("categories").document(categoryId).delete().await()
    }

    suspend fun countCategoriesWithName(uid: String, name: String, excludingId: String?): Int {
        val snapshot = userDoc(uid).collection("categories").get().await()
        return snapshot.documents.count { doc ->
            doc.getString("name")?.equals(name, ignoreCase = true) == true && doc.id != excludingId
        }
    }

    data class ExpenseData(
        val expenseId: String = "",
        val categoryId: String? = null,
        val amount: Double = 0.0,
        val description: String = "",
        val expenseDate: Long = 0L,
        val startTime: String = "",
        val endTime: String = "",
        val photoBase64: String? = null
    )

    fun observeExpenses(uid: String): Flow<List<ExpenseData>> = callbackFlow {
        val registration = userDoc(uid).collection("expenses")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val expenses = snapshot?.documents?.map { doc ->
                    ExpenseData(
                        expenseId = doc.id,
                        categoryId = doc.getString("categoryId"),
                        amount = doc.getDouble("amount") ?: 0.0,
                        description = doc.getString("description") ?: "",
                        expenseDate = doc.getLong("expenseDate") ?: 0L,
                        startTime = doc.getString("startTime") ?: "",
                        endTime = doc.getString("endTime") ?: "",
                        photoBase64 = doc.getString("photoBase64")
                    )
                } ?: emptyList()
                trySend(expenses)
            }
        awaitClose { registration.remove() }
    }

    suspend fun addExpense(
        uid: String,
        categoryId: String?,
        amount: Double,
        description: String,
        expenseDate: Long,
        startTime: String,
        endTime: String,
        photoBase64: String?
    ): String {
        val docRef = userDoc(uid).collection("expenses").add(
            mapOf(
                "categoryId" to categoryId,
                "amount" to amount,
                "description" to description,
                "expenseDate" to expenseDate,
                "startTime" to startTime,
                "endTime" to endTime,
                "photoBase64" to photoBase64
            )
        ).await()
        return docRef.id
    }

    suspend fun deleteExpense(uid: String, expenseId: String) {
        userDoc(uid).collection("expenses").document(expenseId).delete().await()
    }

    fun observeTotalSpentForPeriod(uid: String, start: Long, end: Long): Flow<Double> = callbackFlow {
        val registration = userDoc(uid).collection("expenses")
            .whereGreaterThanOrEqualTo("expenseDate", start)
            .whereLessThanOrEqualTo("expenseDate", end)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val total = snapshot?.documents?.sumOf { it.getDouble("amount") ?: 0.0 } ?: 0.0
                trySend(total)
            }
        awaitClose { registration.remove() }
    }

    fun observeCategoryTotalsForPeriod(uid: String, start: Long, end: Long): Flow<Map<String, Double>> = callbackFlow {
        val registration = userDoc(uid).collection("expenses")
            .whereGreaterThanOrEqualTo("expenseDate", start)
            .whereLessThanOrEqualTo("expenseDate", end)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val totals = mutableMapOf<String, Double>()
                snapshot?.documents?.forEach { doc ->
                    val categoryId = doc.getString("categoryId") ?: return@forEach
                    val amount = doc.getDouble("amount") ?: 0.0
                    totals[categoryId] = (totals[categoryId] ?: 0.0) + amount
                }
                trySend(totals)
            }
        awaitClose { registration.remove() }
    }
}