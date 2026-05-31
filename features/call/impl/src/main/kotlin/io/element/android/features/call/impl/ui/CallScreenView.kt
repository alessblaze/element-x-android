/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import io.element.android.compound.theme.ElementTheme.isLightTheme
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.pip.PictureInPictureEvent
import io.element.android.features.call.impl.pip.PictureInPictureState
import io.element.android.features.call.impl.pip.aPictureInPictureState
import io.element.android.features.call.impl.utils.InvalidAudioDeviceReason
import io.element.android.features.call.impl.utils.WebViewAudioManager
import io.element.android.features.call.impl.utils.WebViewPipController
import io.element.android.features.call.impl.utils.WebViewWidgetMessageInterceptor
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import timber.log.Timber

typealias RequestPermissionCallback = (Array<String>) -> Unit

interface CallScreenNavigator {
    fun close()
}

@Composable
internal fun CallScreenView(
    state: CallScreenState,
    pipState: PictureInPictureState,
    onConsoleMessage: (ConsoleMessage) -> Unit,
    requestPermissions: (Array<String>, RequestPermissionCallback) -> Unit,
    modifier: Modifier = Modifier,
) {
    var callWebView by remember { mutableStateOf<WebView?>(null) }

    fun handleBack(fromNative: Boolean = false) {
        when (CallScreenBackPressPolicy.resolve(supportPip = pipState.supportPip, hasWebView = callWebView != null, fromNative)) {
            CallScreenBackPressAction.EnterPictureInPicture ->
                pipState.eventSink(PictureInPictureEvent.EnterPictureInPicture)
            CallScreenBackPressAction.DispatchEscapeToWebView ->
                callWebView?.dispatchEscKeyEvent()
            null -> Timber.d("Back press with unsupported pip is a no-op")
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = if (isLightTheme) Color(0xffc8abe0) else Color(0xff210538),
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        BackHandler {
            handleBack(fromNative = true)
        }
        if (state.webViewError != null) {
            ErrorDialog(
                content = buildString {
                    append(stringResource(CommonStrings.error_unknown))
                    state.webViewError.takeIf { it.isNotEmpty() }?.let { append("\n\n").append(it) }
                },
                onSubmit = { state.eventSink(CallScreenEvent.Hangup) },
            )
        } else {
            var webViewAudioManager by remember { mutableStateOf<WebViewAudioManager?>(null) }
            val coroutineScope = rememberCoroutineScope()

            var invalidAudioDeviceReason by remember { mutableStateOf<InvalidAudioDeviceReason?>(null) }
            invalidAudioDeviceReason?.let {
                InvalidAudioDeviceDialog(invalidAudioDeviceReason = it) {
                    invalidAudioDeviceReason = null
                }
            }

            CallWebView(
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .fillMaxSize(),
                url = state.urlState,
                userAgent = state.userAgent,
                onPermissionsRequest = { request ->
                    val androidPermissions = mapWebkitPermissions(request.resources)
                    val callback: RequestPermissionCallback = { request.grant(it) }
                    requestPermissions(androidPermissions.toTypedArray(), callback)
                },
                onConsoleMessage = onConsoleMessage,
                onCreateWebView = { webView ->
                    callWebView = webView
                    webView.addBackHandler(onBackPressed = ::handleBack)
                    val interceptor = WebViewWidgetMessageInterceptor(
                        webView = webView,
                        onUrlLoaded = { url ->
                            webView.evaluateJavascript("controls.onBackButtonPressed = () => { backHandler.onBackPressed() }", null)
                            if (webViewAudioManager?.isInCallMode?.get() == false) {
                                Timber.d("URL $url is loaded, starting in-call audio mode")
                                webViewAudioManager?.onCallStarted()
                            } else {
                                Timber.d("Can't start in-call audio mode since the app is already in it.")
                            }
                        },
                        onError = { state.eventSink(CallScreenEvent.OnWebViewError(it)) },
                    )
                    webViewAudioManager = WebViewAudioManager(
                        webView = webView,
                        coroutineScope = coroutineScope,
                        onInvalidAudioDeviceAdded = { invalidAudioDeviceReason = it },
                    )
                    state.eventSink(CallScreenEvent.SetupMessageChannels(interceptor))
                    val pipController = WebViewPipController(webView)
                    pipState.eventSink(PictureInPictureEvent.SetPipController(pipController))
                },
                onDestroyWebView = {
                    callWebView = null
                    // Reset audio mode
                    webViewAudioManager?.onCallStopped()
                }
            )
            when (state.urlState) {
                AsyncData.Uninitialized,
                is AsyncData.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingWaveform()
                    }
                }

                is AsyncData.Failure -> {
                    Timber.e(state.urlState.error, "WebView failed to load URL: ${state.urlState.error.message}")
                    ErrorDialog(
                        content = state.urlState.error.message.orEmpty(),
                        onSubmit = { state.eventSink(CallScreenEvent.Hangup) },
                    )
                }

                is AsyncData.Success -> Unit
            }
        }
    }
}

@Composable
private fun InvalidAudioDeviceDialog(
    invalidAudioDeviceReason: InvalidAudioDeviceReason,
    onDismiss: () -> Unit,
) {
    ErrorDialog(
        content = when (invalidAudioDeviceReason) {
            InvalidAudioDeviceReason.BT_AUDIO_DEVICE_DISABLED -> {
                stringResource(R.string.call_invalid_audio_device_bluetooth_devices_disabled)
            }
        },
        onSubmit = onDismiss,
    )
}

@Composable
private fun CallWebView(
    url: AsyncData<String>,
    userAgent: String,
    onPermissionsRequest: (PermissionRequest) -> Unit,
    onConsoleMessage: (ConsoleMessage) -> Unit,
    onCreateWebView: (WebView) -> Unit,
    onDestroyWebView: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("WebView - can't be previewed")
        }
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    onCreateWebView(this)
                    setup(
                        userAgent = userAgent,
                        onPermissionsRequested = onPermissionsRequest,
                        onConsoleMessage = onConsoleMessage,
                    )
                }
            },
            update = { webView ->
                if (url is AsyncData.Success && webView.url != url.data) {
                    webView.loadUrl(url.data)
                }
            },
            onRelease = { webView ->
                onDestroyWebView(webView)
                webView.destroy()
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.setup(
    userAgent: String,
    onPermissionsRequested: (PermissionRequest) -> Unit,
    onConsoleMessage: (ConsoleMessage) -> Unit,
) {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    with(settings) {
        javaScriptEnabled = true
        allowContentAccess = true
        allowFileAccess = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        @Suppress("DEPRECATION")
        databaseEnabled = true
        loadsImagesAutomatically = true
        userAgentString = userAgent
    }

    webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            onPermissionsRequested(request)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            onConsoleMessage(consoleMessage)
            return true
        }

        override fun getDefaultVideoPoster(): android.graphics.Bitmap {
            return createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        }
    }
}

private fun WebView.addBackHandler(onBackPressed: () -> Unit) {
    addJavascriptInterface(
        JavascriptBackHandlerBridge(callback = onBackPressed),
        "backHandler"
    )
}

@Composable
private fun LoadingWaveform() {
    val barCount = 50
    var heights by remember {
        mutableStateOf(List(barCount) {
            kotlin.random.Random.nextFloat() * 80f + 10f
        })
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            heights = heights.drop(1) + listOf(
                (heights.lastOrNull() ?: 30f).let { last ->
                    val change = (kotlin.random.Random.nextFloat() - 0.5f) * 30
                    (last + change).coerceIn(10f, 90f)
                }
            )
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(100.dp)
    ) {
        heights.forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(height / 100f)
                    .background(
                        color = if (isLightTheme) Color(0xff6f3e99) else Color(0xffc8abe0),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .alpha(if (index < 10) index / 10f else 1f)
            )
        }
    }
}

private fun WebView.dispatchEscKeyEvent() {
    dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ESCAPE))
    dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ESCAPE))
}

@PreviewsDayNight
@Composable
internal fun CallScreenViewPreview(
    @PreviewParameter(CallScreenStateProvider::class) state: CallScreenState,
) = ElementPreview {
    CallScreenView(
        state = state,
        pipState = aPictureInPictureState(),
        requestPermissions = { _, _ -> },
        onConsoleMessage = {},
    )
}

@PreviewsDayNight
@Composable
internal fun InvalidAudioDeviceDialogPreview() = ElementPreview {
    InvalidAudioDeviceDialog(invalidAudioDeviceReason = InvalidAudioDeviceReason.BT_AUDIO_DEVICE_DISABLED) {}
}

internal class JavascriptBackHandlerBridge(
    private val callback: () -> Unit,
) {
    @JavascriptInterface
    fun onBackPressed() {
        callback()
    }
}
