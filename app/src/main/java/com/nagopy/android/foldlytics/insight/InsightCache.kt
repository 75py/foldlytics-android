package com.nagopy.android.foldlytics.insight

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONObject

/** One derived result, with the same local-only retention as the rest of the app. */
internal class InsightCache(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "usage-insight.json"))

    fun read(key: String, facts: UsageInsightFacts): List<InsightSentence>? = try {
        val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
        if (json.getString("key") == key) InsightTextProtocol.parse(json.getString("output"), facts) else null
    } catch (_: Exception) {
        null
    }

    fun write(key: String, output: String) {
        val stream = file.startWrite()
        try {
            stream.write(JSONObject().put("key", key).put("output", output).toString().toByteArray())
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
