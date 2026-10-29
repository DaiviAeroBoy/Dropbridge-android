package com.dropbridge.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    private var server: LocalServer? = null
    private var fbc: Filebin? = null

    private val pick = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        chooser = null
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

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

            @JavascriptInterface
            fun save(path: String, name: String) { runOnUiThread { savePath(path, name) } }
        }, "DropBridgeApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                if (u.host == "127.0.0.1") return false
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
        // Fallback for links that still trigger a normal WebView download
        web.setDownloadListener { url, _, cd, mime, _ ->
            val u = Uri.parse(url)
            val p = u.path ?: ""
            if (fbc != null && u.host == "127.0.0.1" && p.startsWith("/f/")) {
                savePath(p, p.removePrefix("/f/"))
            } else {
                saveFile(url, CookieManager.getInstance().getCookie(url), URLUtil.guessFileName(url, cd, mime), mime)
            }
        }

        val bin = Store.bin(this)
        val saved = Store.get(this)
        when {
            bin != null -> startOnline(bin)
            saved != null -> {
                gate.visibility = View.GONE
                web.loadUrl(saved)
            }
            else -> showGate("")
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }

    private fun startOnline(bin: String) {
        server?.stop()
        val f = Filebin(bin)
        val s = LocalServer(this, f)
        s.start()
        server = s
        fbc = f
        gate.visibility = View.GONE
        web.loadUrl("http://127.0.0.1:${s.port}/")
    }

    /** Save a file the page points at (path like /f/<id>?dl) into Downloads. */
    private fun savePath(path: String, name: String) {
        val f = fbc
        if (f != null) {
            val id = Uri.parse(path).path.orEmpty().removePrefix("/f/")
            Thread {
                try {
                    val loc = f.presigned(id)
                    runOnUiThread { saveFile(loc, null, name, null) }
                } catch (e: Exception) {
                    runOnUiThread { toast("Couldn't reach Filebin") }
                }
            }.start()
            return
        }
        val saved = Store.get(this) ?: return
        val base = Store.base(saved)
        val cookie = CookieManager.getInstance().getCookie(base) ?: ("t=" + (Store.token(saved) ?: ""))
        saveFile(base + path, cookie, name, null)
    }

    private fun saveFile(url: String, cookie: String?, rawName: String, mime: String?) {
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            toast("Allow storage access, then tap Save again")
            return
        }
        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val n = Saver.save(this, url, cookie, rawName, mime)
                runOnUiThread { toast("Saved to Downloads: $n") }
            } catch (e: Exception) {
                runOnUiThread { toast("Couldn't save: ${e.message}") }
            }
        }.start()
    }

    private fun showGate(msg: String) {
        note.text = if (msg.isEmpty()) "On your PC, open DropBridge, then scan the QR code shown there. Works on Wi-Fi and for online sessions." else msg
        gate.visibility = View.VISIBLE
    }

    private fun connect(url: String) {
        val b = Regex("filebin\\.net/([A-Za-z0-9_-]{6,})").find(url)?.groupValues?.get(1)
        if (b != null) {
            Store.setBin(this, b)
            startOnline(b)
            return
        }
        if (!url.startsWith("http")) {
            note.text = "That doesn't look like a DropBridge link."
            return
        }
        Store.set(this, url)
        server?.stop()
        fbc = null
        gate.visibility = View.GONE
        web.loadUrl(url)
    }

    private fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, opts).startScan()
            .addOnSuccessListener { connect(it.rawValue ?: "") }
            .addOnFailureListener { note.text = "Scanner not available. Paste the link instead." }
    }

    private fun glossy(): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#4387F6"), Color.parseColor("#1B64E3")))
        g.cornerRadius = dp(10).toFloat()
        g.setStroke(dp(1), Color.parseColor("#1652C5"))
        return g
    }

    private fun plain(fill: String): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.parseColor(fill))
        g.cornerRadius = dp(10).toFloat()
        g.setStroke(dp(1), Color.parseColor("#E2E8F0"))
        return g
    }

    private fun buildGate(): LinearLayout {
        val g = LinearLayout(this)
        g.orientation = LinearLayout.VERTICAL
        g.gravity = Gravity.CENTER
        g.setBackgroundColor(Color.parseColor("#F4F7FC"))
        g.setPadding(dp(28), dp(28), dp(28), dp(28))

        val title = TextView(this)
        title.text = "DropBridge"
        title.textSize = 26f
        title.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        title.setTextColor(Color.parseColor("#1B2330"))
        g.addView(title)

        note = TextView(this)
        note.textSize = 14f
        note.gravity = Gravity.CENTER
        note.setTextColor(Color.parseColor("#6B7686"))
        note.setPadding(0, dp(10), 0, dp(24))
        g.addView(note)

        val scan = Button(this)
        scan.text = "Scan QR code"
        scan.isAllCaps = false
        scan.textSize = 15f
        scan.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        scan.setTextColor(Color.WHITE)
        scan.background = glossy()
        scan.stateListAnimator = null
        scan.setOnClickListener { scan() }
        g.addView(scan, LinearLayout.LayoutParams(-1, dp(48)))

        val input = EditText(this)
        input.hint = "or paste a link"
        input.textSize = 15f
        input.setSingleLine()
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.background = plain("#F8FAFC")
        input.setPadding(dp(14), 0, dp(14), 0)
        val lp = LinearLayout.LayoutParams(-1, dp(48))
        lp.topMargin = dp(16)
        g.addView(input, lp)

        val go = Button(this)
        go.text = "Connect"
        go.isAllCaps = false
        go.textSize = 15f
        go.setTextColor(Color.parseColor("#1B2330"))
        go.background = plain("#FFFFFF")
        go.stateListAnimator = null
        go.setOnClickListener { connect(input.text.toString().trim()) }
        val lp2 = LinearLayout.LayoutParams(-1, dp(48))
        lp2.topMargin = dp(10)
        g.addView(go, lp2)
        return g
    }
}
