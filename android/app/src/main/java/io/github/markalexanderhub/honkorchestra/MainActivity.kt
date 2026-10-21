package io.github.markalexanderhub.honkorchestra

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File

/**
 * The app is the website in a WebView, served from the app's own files over a secure local address
 * so the microphone is allowed. It adds what a browser tab can't do well: stay awake, and save and
 * share recordings.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var pendingMic: PermissionRequest? = null
    private var lastFile: File? = null
    private var pendingFiles: android.webkit.ValueCallback<Array<Uri>>? = null

    // "Remix a file": the page's file picker needs the app to open the system one.
    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingFiles?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        pendingFiles = null
    }

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pendingMic?.let { if (ok) it.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else it.deny() }
        pendingMic = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this)
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.road))
        // WebView ignores its own padding, so the system-bar gap goes on a frame around it.
        val frame = android.widget.FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.road))
            addView(web)
        }
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: android.webkit.ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                pendingFiles?.onReceiveValue(null)
                pendingFiles = callback
                pickFile.launch("*/*")
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE !in request.resources) { request.deny(); return@runOnUiThread }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else {
                        pendingMic = request
                        askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.addJavascriptInterface(Bridge(), "HonkApp")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        onBackPressedDispatcher.addCallback(this) { finish() }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    inner class Bridge {
        /** Saves a recording to Downloads/Honk Orchestra and keeps a copy for sharing. Returns where it went. */
        @JavascriptInterface
        fun save(name: String, mime: String, base64: String): String {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val dir = File(cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            lastFile = File(dir, name).apply { writeBytes(bytes) }
            return runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime.substringBefore(';'))
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Honk Orchestra")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                    contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                    "Downloads/Honk Orchestra"
                } else {
                    val out = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), name)
                    out.writeBytes(bytes)
                    "the app's Music folder"
                }
            }.getOrDefault("the app (tap Share to send it)")
        }

        @JavascriptInterface
        fun share() {
            val f = lastFile ?: return
            runOnUiThread {
                val uri: Uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", f)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "The street, remixed with Honk Orchestra.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Share your remix"))
            }
        }
    }
}
