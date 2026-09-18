package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        ChatsPapScreen()
      }
    }
  }
}

private const val PRIMARY_URL = "https://null.perchance.org/chatspap"
private const val FALLBACK_URL = "https://perchance.org/chatspap"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatsPapScreen() {
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var canGoBack by remember { mutableStateOf(false) }
  var progress by remember { mutableFloatStateOf(0f) }
  var isLoading by remember { mutableStateOf(true) }
  var hasError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }

  var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

  val fileChooserLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val results: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
      val data: Intent? = result.data
      when {
        data?.data != null -> arrayOf(data.data!!)
        data?.clipData != null -> {
          val clip = data.clipData!!
          Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
        }
        else -> null
      }
    } else {
      null
    }
    filePathCallback?.onReceiveValue(results)
    filePathCallback = null
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { _ -> }

  BackHandler(enabled = canGoBack) {
    webViewInstance?.goBack()
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("chatspap_scaffold"),
    contentWindowInsets = WindowInsets(0, 0, 0, 0)
  ) { _ ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
      AndroidView(
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              allowFileAccess = true
              allowContentAccess = true
              useWideViewPort = true
              loadWithOverviewMode = true
              mediaPlaybackRequiresUserGesture = false
              mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
              cacheMode = WebSettings.LOAD_DEFAULT
            }

            val removeHeaderJs = """
              (function() {
                function stripPerchanceHeader() {
                  var styleId = 'perchance-no-header-override';
                  if (!document.getElementById(styleId)) {
                    var style = document.createElement('style');
                    style.id = styleId;
                    style.type = 'text/css';
                    style.textContent = `
                      #topNav, #top-bar, #header, header, .header, #banner, .banner,
                      #perchance-top-bar, #perchance-nav, .perchance-header,
                      #nav, #topbar, .site-header, #top-menu,
                      #output-container > div:first-child:not(#output) {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0px !important;
                        min-height: 0px !important;
                        max-height: 0px !important;
                        margin: 0px !important;
                        padding: 0px !important;
                        overflow: hidden !important;
                      }
                      html, body {
                        margin: 0 !important;
                        padding: 0 !important;
                        width: 100% !important;
                        height: 100% !important;
                        overflow-x: hidden !important;
                      }
                      #output {
                        position: fixed !important;
                        top: 0px !important;
                        left: 0px !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        border: none !important;
                        z-index: 999999 !important;
                      }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                  }
                  var iframe = document.getElementById('output');
                  if (iframe) {
                    iframe.style.position = 'fixed';
                    iframe.style.top = '0px';
                    iframe.style.left = '0px';
                    iframe.style.width = '100vw';
                    iframe.style.height = '100vh';
                    iframe.style.zIndex = '999999';
                    iframe.style.border = 'none';
                  }
                }

                stripPerchanceHeader();
                if (document.readyState === 'loading') {
                  document.addEventListener('DOMContentLoaded', stripPerchanceHeader);
                }
                window.addEventListener('load', stripPerchanceHeader);

                if (window.MutationObserver) {
                  var observer = new MutationObserver(function() {
                    stripPerchanceHeader();
                  });
                  observer.observe(document.documentElement || document.body, { childList: true, subtree: true });
                }
              })();
            """.trimIndent()

            webViewClient = object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isLoading = true
                hasError = false
                canGoBack = view?.canGoBack() == true
                view?.evaluateJavascript(removeHeaderJs, null)
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
                canGoBack = view?.canGoBack() == true
                view?.evaluateJavascript(removeHeaderJs, null)
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                  hasError = true
                  isLoading = false
                  errorMessage = error?.description?.toString() ?: "Gagal memuat halaman web."
                }
              }

              override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
              ): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.startsWith("http://") || url.startsWith("https://")) {
                  false
                } else {
                  try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    ctx.startActivity(intent)
                  } catch (_: Exception) { }
                  true
                }
              }

              override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
              ): Boolean {
                view?.destroy()
                return true
              }
            }

            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progress = newProgress / 100f
                if (newProgress >= 100) {
                  isLoading = false
                }
                view?.evaluateJavascript(removeHeaderJs, null)
              }

              override fun onShowFileChooser(
                webView: WebView?,
                filePathCallbackParam: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
              ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = filePathCallbackParam

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                  type = "*/*"
                  addCategory(Intent.CATEGORY_OPENABLE)
                }

                return try {
                  fileChooserLauncher.launch(intent)
                  true
                } catch (_: Exception) {
                  filePathCallback = null
                  false
                }
              }

              override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                  val permissionsToRequest = mutableListOf<String>()
                  for (resource in it.resources) {
                    if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                      permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
                    } else if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                      permissionsToRequest.add(android.Manifest.permission.CAMERA)
                    }
                  }
                  if (permissionsToRequest.isNotEmpty()) {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                  }
                  it.grant(it.resources)
                }
              }

              override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return super.onConsoleMessage(consoleMessage)
              }
            }

            loadUrl(PRIMARY_URL)
            webViewInstance = this
          }
        },
        modifier = Modifier
          .fillMaxSize()
          .testTag("chatspap_webview")
      )

      // Top progress bar during page load
      AnimatedVisibility(
        visible = isLoading && progress > 0f && progress < 1f,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
      ) {
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .testTag("chatspap_progress_bar"),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
      }

      // Offline / Error screen
      if (hasError) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .padding(24.dp)
            .testTag("chatspap_error_container"),
          contentAlignment = Alignment.Center
        ) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Offline",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
              )
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = "Gagal Memuat Konten",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = if (errorMessage.isNotBlank()) errorMessage else "Periksa koneksi internet Anda dan coba lagi.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(24.dp))
              Button(
                onClick = {
                  hasError = false
                  isLoading = true
                  webViewInstance?.loadUrl(PRIMARY_URL)
                },
                modifier = Modifier.testTag("chatspap_retry_button"),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary
                )
              ) {
                Icon(
                  imageVector = Icons.Default.Refresh,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Coba Lagi")
              }
            }
          }
        }
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewInstance?.apply {
        stopLoading()
        clearHistory()
        destroy()
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}

