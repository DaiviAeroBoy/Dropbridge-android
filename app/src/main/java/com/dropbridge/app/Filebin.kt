package com.dropbridge.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** One online session = one Filebin bin. Mirrors the PC server's online mode. */
class Filebin(private val bin: String) {
    private val host = "https://filebin.net"
    private val jar = ConcurrentHashMap<String, String>()
    private val texts = ConcurrentHashMap<String, String>()
    private val name = Regex("^([tf])([PM])-(\\d+)-[a-z0-9]{4}-(.*)$")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun open(path: String, method: String): HttpURLConnection {
        val c = URL(host + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.instanceFollowRedirects = false
        c.connectTimeout = 10000
        c.readTimeout = 60000
        if (jar.isNotEmpty()) c.setRequestProperty("Cookie", jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
        return c
    }

    private fun keep(c: HttpURLConnection) {
        for ((k, v) in c.headerFields) {
            if (k != null && k.equals("set-cookie", true)) {
                for (s in v) {
                    val kv = s.substringBefore(';')
                    val i = kv.indexOf('=')
                    if (i > 0) jar[kv.substring(0, i).trim()] = kv.substring(i + 1)
                }
            }
        }
    }

    /** Downloads redirect to a short-lived presigned URL. The first request may only set a verification cookie. */
    fun presigned(file: String): String {
        repeat(3) {
            val c = open("/$bin/${enc(file)}", "GET")
            val code = c.responseCode
            if (code in 300..399) {
                val l = c.getHeaderField("Location")
                c.disconnect()
                return l
            }
            keep(c)
            if (code != 200) {
                c.disconnect()
                throw IOException("download $code")
            }
            c.inputStream.use { it.readBytes() }
        }
        throw IOException("verification failed")
    }

    private fun textOf(file: String): String =
        texts.getOrPut(file) { URL(presigned(file)).readText() }

    private fun files(): JSONArray {
        val c = open("/$bin", "GET")
        c.setRequestProperty("Accept", "application/json")
        val code = c.responseCode
        if (code == 404) { c.disconnect(); return JSONArray() }
        if (code != 200) { c.disconnect(); throw IOException("list $code") }
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        return j.optJSONArray("files") ?: JSONArray()
    }

    fun list(): JSONArray {
        val fs = files()
        val out = ArrayList<JSONObject>()
        for (i in 0 until fs.length()) {
            val f = fs.getJSONObject(i)
            val n = f.getString("filename")
            val m = name.matchEntire(n)
            val orig = m?.groupValues?.get(4) ?: n
            val kind = if (m?.groupValues?.get(1) == "t") "text" else "file"
            val o = JSONObject()
            o.put("id", n)
            o.put("kind", kind)
            o.put("name", orig)
            o.put("size", f.optLong("bytes"))
            o.put("mime", mimeOf(orig))
            o.put("from", when (m?.groupValues?.get(2)) { "P" -> "PC"; "M" -> "Phone"; else -> "Web" })
            val t = m?.groupValues?.get(3)?.toLongOrNull()
                ?: runCatching { Instant.parse(f.optString("created_at")).toEpochMilli() }.getOrDefault(0L)
            o.put("t", t)
            if (kind == "text") o.put("text", try { textOf(n) } catch (e: Exception) { "(could not load)" })
            out.add(o)
        }
        out.sortByDescending { it.getLong("t") }
        return JSONArray(out)
    }

    private fun nm(kind: String, orig: String): String {
        val r = Integer.toHexString((Math.random() * 65536).toInt()).padStart(4, '0')
        val safe = orig.replace(Regex("[^A-Za-z0-9._-]+"), "_").trimStart('.').takeLast(100).ifEmpty { "file" }
        return "${kind}M-${System.currentTimeMillis()}-$r-$safe"
    }

    private fun upload(file: String, len: Long, input: InputStream) {
        if (len < 0) {
            // Filebin needs Content-Length, so measure unknown sizes via a temp file
            val tmp = File.createTempFile("db", null)
            try {
                tmp.outputStream().use { input.copyTo(it) }
                FileInputStream(tmp).use { upload(file, tmp.length(), it) }
            } finally { tmp.delete() }
            return
        }
        val c = open("/$bin/${enc(file)}", "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.setFixedLengthStreamingMode(len)
        c.outputStream.use { input.copyTo(it) }
        val code = c.responseCode
        c.disconnect()
        if (code != 201) throw IOException("upload $code")
    }

    fun sendText(text: String) {
        val b = text.trim().toByteArray()
        val n = nm("t", "text.txt")
        upload(n, b.size.toLong(), ByteArrayInputStream(b))
        texts[n] = text.trim()
    }

    fun sendFile(orig: String, len: Long, input: InputStream) = upload(nm("f", orig), len, input)

    fun delete(id: String) {
        val c = open("/$bin/${enc(id)}", "DELETE")
        c.responseCode
        c.disconnect()
    }

    fun deleteAll() {
        val fs = files()
        for (i in 0 until fs.length()) delete(fs.getJSONObject(i).getString("filename"))
    }

    private fun mimeOf(n: String): String = when (n.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
