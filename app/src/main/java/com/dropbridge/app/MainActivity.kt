package com.dropbridge.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var gate: LinearLayout
    private lateinit var note: TextView
    private var chooser: ValueCallback<Array<Uri>>? = null

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        chooser = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        gate = buildGate()
        root.addView(gate, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun rescan() { runOnUiThread { showGate("") } }
        }, "DropBridgeApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                val cur = Store.get(this@MainActivity)?.let { Uri.parse(it).host }
                if (u.host == cur) return false
                startActivity(Intent(Intent.ACTION_VIEW, u))
                return true
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    showGate("Can't reach your PC. Make sure DropBridge is running and both are on the same Wi-Fi or hotspot, then scan again.")
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                chooser?.onReceiveValue(null)
                chooser = cb
                return try {
                    pick.launch(p!!.createIntent()); true
                } catch (e: Exception) {
                    chooser = null; false
                }
            }
        }
        web.setDownloadListener { url, _, cd, mime, _ -> download(url, cd, mime) }

        val saved = Store.get(this)
        if (saved != null) {
            gate.visibility = View.GONE
            web.loadUrl(saved)
        } else showGate("")
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    private fun showGate(msg: String) {
        note.text = if (msg.isEmpty()) "On your PC, open DropBridge, then scan the QR code shown there." else msg
        gate.visibility = View.VISIBLE
    }

    private fun connect(url: String) {
        if (!url.startsWith("http")) {
            note.text = "That doesn't look like a DropBridge link."
            return
        }
        Store.set(this, url)
        gate.visibility = View.GONE
        web.loadUrl(url)
    }

    private fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, opts).startScan()
            .addOnSuccessListener { connect(it.rawValue ?: "") }
            .addOnFailureListener { note.text = "Scanner not available. Paste the link instead." }
    }

    private fun download(url: String, cd: String?, mime: String?) {
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            Toast.makeText(this, "Allow storage, then tap Save again", Toast.LENGTH_LONG).show()
            return
        }
        val name = URLUtil.guessFileName(url, cd, mime)
        val r = DownloadManager.Request(Uri.parse(url))
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        if (!mime.isNullOrEmpty()) r.setMimeType(mime)
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
        Toast.makeText(this, "Saving $name to Downloads", Toast.LENGTH_SHORT).show()
    }

    private fun buildGate(): LinearLayout {
        val g = LinearLayout(this)
        g.orientation = LinearLayout.VERTICAL
        g.gravity = Gravity.CENTER
        g.setBackgroundColor(Color.parseColor("#ECEEF1"))
        g.setPadding(64, 64, 64, 64)
        val title = TextView(this)
        title.text = "DropBridge"
        title.textSize = 26f
        title.setTextColor(Color.parseColor("#1F2328"))
        g.addView(title)
        note = TextView(this)
        note.textSize = 14f
        note.gravity = Gravity.CENTER
        note.setTextColor(Color.parseColor("#6A7280"))
        note.setPadding(0, 16, 0, 32)
        g.addView(note)
        val scan = Button(this)
        scan.text = "Scan QR code"
        scan.setBackgroundColor(Color.parseColor("#2F6BFF"))
        scan.setTextColor(Color.WHITE)
        scan.setOnClickListener { scan() }
        g.addView(scan)
        val input = EditText(this)
        input.hint = "or paste link"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        g.addView(input)
        val go = Button(this)
        go.text = "Connect"
        go.setOnClickListener { connect(input.text.toString().trim()) }
        g.addView(go)
        return g
    }
}
