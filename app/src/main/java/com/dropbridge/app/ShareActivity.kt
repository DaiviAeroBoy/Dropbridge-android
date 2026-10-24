package com.dropbridge.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ShareActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = Store.get(this)
        if (saved == null) {
            Toast.makeText(this, "Open DropBridge and scan the QR code first", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val base = Store.base(saved)
        val tk = Store.token(saved) ?: ""
        val uris = ArrayList<Uri>()
        if (intent.action == Intent.ACTION_SEND) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        Toast.makeText(this, "Sending to PC...", Toast.LENGTH_SHORT).show()
        Thread {
            var ok = true
            try {
                if (!text.isNullOrBlank()) postText(base, tk, text)
                for (u in uris) upload(base, tk, u)
            } catch (e: Exception) {
                ok = false
            }
            runOnUiThread {
                Toast.makeText(this, if (ok) "Sent to PC" else "Couldn't reach your PC", Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }

    private fun open(path: String, base: String, tk: String): HttpURLConnection {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 8000
        c.readTimeout = 120000
        c.setRequestProperty("Cookie", "t=$tk")
        return c
    }

    private fun postText(base: String, tk: String, text: String) {
        val c = open("/api/text", base, tk)
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(JSONObject().put("text", text).toString().toByteArray()) }
        if (c.responseCode !in 200..299) throw IOException("text failed")
        c.disconnect()
    }

    private fun upload(base: String, tk: String, uri: Uri) {
        var name = "file"
        contentResolver.query(uri, null, null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = cur.getString(i)
            }
        }
        val c = open("/api/upload", base, tk)
        c.setChunkedStreamingMode(64 * 1024)
        c.setRequestProperty("Content-Type", contentResolver.getType(uri) ?: "application/octet-stream")
        c.setRequestProperty("x-filename", URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
        contentResolver.openInputStream(uri)!!.use { input -> c.outputStream.use { out -> input.copyTo(out) } }
        if (c.responseCode !in 200..299) throw IOException("upload failed")
        c.disconnect()
    }
}
