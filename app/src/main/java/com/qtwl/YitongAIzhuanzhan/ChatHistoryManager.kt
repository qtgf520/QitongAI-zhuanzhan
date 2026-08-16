package com.qtwl.YitongAIzhuanzhan

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ChatHistoryManager {
    private const val TAG = "ChatHistory"
    private const val FILE_NAME = "chat_history.json"
    private const val MAX_RECORDS = 100

    fun getAllMessages(context: Context): List<ChatMessage> {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            val json = JSONObject(file.readText())
            val arr = json.getJSONArray("messages")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ChatMessage(
                    platformId = obj.getString("platformId"),
                    platformName = obj.getString("platformName"),
                    userMessage = obj.getString("userMessage"),
                    aiReply = obj.getString("aiReply"),
                    timestamp = obj.getLong("timestamp"),
                    replyLength = obj.getInt("replyLength")
                )
            }.sortedByDescending { it.timestamp }.take(MAX_RECORDS)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMessage(
        context: Context,
        platformId: String,
        platformName: String,
        userMessage: String,
        aiReply: String
    ) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val json = if (file.exists()) JSONObject(file.readText()) else JSONObject().put("messages", JSONArray())
            val arr = json.getJSONArray("messages")
            val msg = JSONObject().apply {
                put("platformId", platformId)
                put("platformName", platformName)
                put("userMessage", userMessage)
                put("aiReply", aiReply)
                put("timestamp", System.currentTimeMillis())
                put("replyLength", aiReply.length)
            }
            arr.put(msg)
            while (arr.length() > MAX_RECORDS) {
                arr.remove(0)
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "saveMessage failed", e)
        }
    }

    fun deleteMessage(context: Context, timestamp: Long) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return
            val json = JSONObject(file.readText())
            val arr = json.getJSONArray("messages")
            for (i in arr.length() - 1 downTo 0) {
                if (arr.getJSONObject(i).getLong("timestamp") == timestamp) {
                    arr.remove(i)
                    break
                }
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "deleteMessage failed", e)
        }
    }

    fun deleteAll(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (e: Exception) {
            Log.e(TAG, "deleteAll failed", e)
        }
    }
}

data class ChatMessage(
    val platformId: String,
    val platformName: String,
    val userMessage: String,
    val aiReply: String,
    val timestamp: Long,
    val replyLength: Int
)