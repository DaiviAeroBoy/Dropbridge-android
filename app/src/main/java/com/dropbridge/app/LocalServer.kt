package com.dropbridge.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/** Tiny HTTP server on 127.0.0.1 so the same web UI can run inside the app, backed by Filebin. */
class LocalServer(private val ctx: Context, private val fb: Filebin) {
    private val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = ss.localPort

    fun start() {
        Thread {
            while (!ss.isClosed) {
                try {
                    val s = ss.accept()
                    Thread { handle(s) }.start()
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
    }

    fun stop() {
        try { ss.close() } catch (e: Exception) { }
    }

    private class Limited(private val i: InputStream, private var left: Long) : InputStream() {
        override fun read(): Int {
            if (left <= 0) return -1
            val b = i.read()
            if (b >= 0) left--
            return b
        }
        override fun read(b: ByteArray, o: Int, l: Int): Int {
            if (left <= 0) return -1
            val n = i.read(b, o, minOf(l.toLong(), left).toInt())
            if (n > 0) left -= n
            return n
        }
    }

    private fun send(out: OutputStream, code: Int, type: String, body: ByteArray, extra: String = "") {
        out.write("HTTP/1.1 $code OK\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n$extra\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun asset(n: String): ByteArray = ctx.assets.open(n).use { it.readBytes() }

    private fun handle(s: Socket) {
        s.use {
            try {
                val inp = BufferedInputStream(s.getInputStream())
                val out = s.getOutputStream()
                val head = ByteArrayOutputStream()
                var last = 0
                while (true) {
                    val b = inp.read()
                    if (b < 0) return
                    head.write(b)
                    last = (last shl 8) or b
                    if (last == 0x0D0A0D0A) break
                    if (head.size() > 65536) return
                }
                val lines = head.toString("UTF-8").split("\r\n")
                val first = lines[0].split(" ")
                val hdr = HashMap<String, String>()
                for (l in lines.drop(1)) {
                    val i = l.indexOf(':')
                    if (i > 0) hdr[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
                }
                route(first[0], first[1].substringBefore('?'), hdr, inp, out)
            } catch (e: Exception) {
            }
        }
    }

    private fun route(m: String, p: String, h: Map<String, String>, inp: InputStream, out: OutputStream) {
        val json = "application/json"
        val ok = """{"ok":1}""".toByteArray()
        try {
            when {
                m == "GET" && p == "/" -> send(out, 200, "text/html; charset=utf-8", asset("index.html"))
                m == "GET" && p == "/qr.js" -> send(out, 200, "text/javascript", asset("qr.js"))
                p == "/api/info" -> send(out, 200, json, """{"local":false,"online":true}""".toByteArray())
                m == "GET" && p == "/api/items" -> send(out, 200, json, fb.list().toString().toByteArray())
                m == "DELETE" && p == "/api/items" -> { fb.deleteAll(); send(out, 200, json, ok) }
                m == "POST" && p == "/api/text" -> {
                    val len = h["content-length"]?.toInt() ?: 0
                    val buf = ByteArray(len)
                    var got = 0
                    while (got < len) {
                        val n = inp.read(buf, got, len - got)
                        if (n < 0) break
                        got += n
                    }
                    fb.sendText(JSONObject(String(buf, 0, got)).getString("text"))
                    send(out, 200, json, ok)
                }
                m == "POST" && p == "/api/upload" -> {
                    val len = h["content-length"]?.toLongOrNull() ?: 0L
                    val n = URLDecoder.decode(h["x-filename"] ?: "file", "UTF-8")
                    fb.sendFile(n, len, Limited(inp, len))
                    send(out, 200, json, ok)
                }
                m == "DELETE" && p.startsWith("/api/items/") -> {
                    fb.delete(URLDecoder.decode(p.removePrefix("/api/items/"), "UTF-8"))
                    send(out, 200, json, ok)
                }
                m == "GET" && p.startsWith("/f/") -> {
                    val loc = fb.presigned(URLDecoder.decode(p.removePrefix("/f/"), "UTF-8"))
                    send(out, 302, "text/plain", ByteArray(0), "Location: $loc\r\n")
                }
                else -> send(out, 404, json, """{"error":"nf"}""".toByteArray())
            }
        } catch (e: Exception) {
            try { send(out, 502, json, """{"error":"upstream"}""".toByteArray()) } catch (x: Exception) { }
        }
    }
}
