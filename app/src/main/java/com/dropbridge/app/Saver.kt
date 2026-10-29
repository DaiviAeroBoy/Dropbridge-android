package com.dropbridge.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

/** Saves a file into the public Downloads folder. Does its own download, no DownloadManager. */
object Saver {
    fun clean(raw: String): String {
        val n = raw.replace(Regex("^[tf][PM]-\\d+-[a-z0-9]{4}-"), "")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
        return n.ifEmpty { "file" }
    }

    fun save(ctx: Context, url: String, cookie: String?, rawName: String, mime: String?): String {
        val name = clean(rawName)
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 60000
        if (!cookie.isNullOrEmpty()) c.setRequestProperty("Cookie", cookie)
        val code = c.responseCode
        if (code !in 200..299) {
            c.disconnect()
            throw IOException("server said $code")
        }
        val type = mime ?: URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
        c.inputStream.use { input ->
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues()
                v.put(MediaStore.Downloads.DISPLAY_NAME, name)
                v.put(MediaStore.Downloads.MIME_TYPE, type)
                v.put(MediaStore.Downloads.IS_PENDING, 1)
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                    ?: throw IOException("can't create file")
                ctx.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
                val done = ContentValues()
                done.put(MediaStore.Downloads.IS_PENDING, 0)
                ctx.contentResolver.update(uri, done, null, null)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                var f = File(dir, name)
                var i = 1
                while (f.exists()) {
                    f = File(dir, name.substringBeforeLast('.') + " ($i)" + (if (name.contains('.')) "." + name.substringAfterLast('.') else ""))
                    i++
                }
                FileOutputStream(f).use { input.copyTo(it) }
                MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), null, null)
            }
        }
        c.disconnect()
        return name
    }
}
