package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SavedReplyRepository(private val dao: SavedReplyDao) {

    val allReplies: Flow<List<SavedReply>> = dao.getAllReplies()

    fun searchReplies(query: String): Flow<List<SavedReply>> {
        return if (query.isBlank()) {
            dao.getAllReplies()
        } else {
            dao.searchReplies("%$query%")
        }
    }

    suspend fun insertReply(reply: SavedReply): Long {
        return dao.insertReply(reply)
    }

    suspend fun updateReply(reply: SavedReply) {
        dao.updateReply(reply)
    }

    suspend fun deleteReply(reply: SavedReply) {
        dao.deleteReply(reply)
    }

    suspend fun incrementUseCount(id: Int) {
        dao.incrementUseCount(id)
    }

    suspend fun prepopulateIfEmpty() {
        try {
            val currentList = dao.getAllReplies().first()
            if (currentList.isEmpty()) {
                val defaults = listOf(
                    SavedReply(
                        title = "শুভেচ্ছা বার্তা (Greeting)",
                        content = "আসসালামু আলাইকুম! আমাদের পেজে যোগাযোগ করার জন্য আপনাকে অনেক ধন্যবাদ। আমরা আপনাকে কীভাবে সাহায্য করতে পারি?",
                        category = "শুভেচ্ছা (General)"
                    ),
                    SavedReply(
                        title = "অর্ডার করার নিয়ম (How to Order)",
                        content = "অর্ডার কনফার্ম করতে দয়া করে আপনার পূর্ণ নাম, সম্পূর্ণ ঠিকানা এবং সচল মোবাইল নাম্বারটি এখানে লিখে দিন। ধন্যবাদ!",
                        category = "অর্ডার (Order)"
                    ),
                    SavedReply(
                        title = "ডেলিভারি চার্জ ও সময় (Delivery Details)",
                        content = "ডেলিভারি চার্জ: ঢাকার মধ্যে ৮০ টাকা, ঢাকার বাইরে ১৫০ টাকা। সময়: ঢাকার ভেতরে ২-৩ দিন, ঢাকার বাইরে ৪-৫ দিন।",
                        category = "ডেলিভারি (Shipping)"
                    ),
                    SavedReply(
                        title = "পেমেন্ট পদ্ধতি (Payment Info)",
                        content = "বিকাশ/নগদ পার্সোনাল নম্বর: ০১৭XXXXXXXX। টাকা সেন্ড মানি করার পর দয়া করে আপনার নম্বরের শেষ ৩টি ডিজিট অথবা স্ক্রিনশট আমাদের মেসেজে পাঠান।",
                        category = "পেমেন্ট (Payment)"
                    ),
                    SavedReply(
                        title = "স্টক শেষ (Out of Stock)",
                        content = "দুঃখিত! এই প্রোডাক্টটি এই মুহূর্তে আউট অফ স্টক রয়েছে। নতুন স্টক ইন হলে আমরা আপনাকে মেসেজে জানিয়ে দিব। আমাদের সাথেই থাকুন!",
                        category = "সহায়তা (Support)"
                    ),
                    SavedReply(
                        title = "ধন্যবাদ বার্তা (Thank You)",
                        content = "আমাদের থেকে কেনাকাটা করার জন্য আপনাকে অনেক ধন্যবাদ! আপনার প্রোডাক্টটি সময়মতো পেয়ে যাবেন আশা করি। ভালো থাকবেন!",
                        category = "শুভেচ্ছা (General)"
                    )
                )
                for (reply in defaults) {
                    dao.insertReply(reply)
                }
                Log.d("SavedReplyRepository", "Prepopulated database with standard business quick replies.")
            }
        } catch (e: Exception) {
            Log.e("SavedReplyRepository", "Error during prepopulation: ${e.message}")
        }
    }
}
