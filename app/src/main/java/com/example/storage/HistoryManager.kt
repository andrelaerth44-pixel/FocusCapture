package com.example.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.detection.CropRect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val id: String,
    val title: String,
    val filePath: String,
    val timestamp: Long,
    val width: Int,
    val height: Int,
    val confidence: Float
)

object HistoryManager {
    private const val PREFS_NAME = "focus_capture_history"
    private const val KEY_ITEMS = "history_items"

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        _items.value = parseItems(jsonStr)
    }

    fun addItem(context: Context, item: HistoryItem) {
        val current = _items.value.toMutableList()
        current.add(0, item)
        _items.value = current
        save(context, current)
    }

    fun removeItem(context: Context, id: String) {
        val current = _items.value.filter { it.id != id }
        _items.value = current
        save(context, current)
    }

    private fun save(context: Context, list: List<HistoryItem>) {
        val array = JSONArray()
        list.take(50).forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("filePath", item.filePath)
                put("timestamp", item.timestamp)
                put("width", item.width)
                put("height", item.height)
                put("confidence", item.confidence)
            }
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun parseItems(jsonStr: String): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HistoryItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        filePath = obj.getString("filePath"),
                        timestamp = obj.getLong("timestamp"),
                        width = obj.getInt("width"),
                        height = obj.getInt("height"),
                        confidence = obj.optDouble("confidence", 0.9).toFloat()
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
