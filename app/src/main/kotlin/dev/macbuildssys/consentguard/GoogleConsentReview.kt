package dev.macbuildssys.consentguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo

/** Inspects both panels in the Google form before requesting submission. */
@TargetApi(30)
internal class GoogleConsentReview(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val packageName: String,
    private val scrollDelayMs: Long,
    private val isCurrentSession: () -> Boolean,
    private val activeRoot: () -> AccessibilityNodeInfo?,
    private val canTouch: (Int, Int) -> Boolean,
    private val readyToConfirm: (() -> Unit) -> Unit,
    private val nextScan: (Long) -> Unit,
    private val finish: (Int) -> Unit
) {
    private class Progress(val panel: GoogleConsentPanel) {
        val controls = panel.controls
        val bodyChildCount = panel.body.childCount
        val checked = mutableSetOf<Int>()
        var vicinity = 0
        var atTop = panel.top
        var lastViewport: List<Pair<Int, Rect>>? = null
        var repeatedViewport = 0
        var lastLookahead = -1
        var forceExactReveal = false
        val pendingSwitches = mutableSetOf<Int>()
        var pendingRechecks = 0
        var unstableReads = 0
        var unknownSwitch: Int? = null
        var unknownReads = 0
    }

    private val completed = mutableSetOf<GoogleConsentPanel.Kind>()
    private var progress: Progress? = null
    private var waiting = false
    private var panelReadRetries = 0
    private var captureReadRetries = 0
    private var lastCaptureRequest: Long? = null

    fun rootUnavailable() {
        if (!waiting) retryPanelRead()
    }

    fun step(root: AccessibilityNodeInfo) {
        if (waiting) return
        var panel = GoogleConsentPanel.read(root, readControls = false) ?: return retryPanelRead()
        val previous = progress?.panel
        if (previous != null && !previous.sameForm(panel)) return stop(R.string.log_google_panel_changed)
        if (previous?.kind != panel.kind) {
            val inventoryStart = SystemClock.uptimeMillis()
            panel = GoogleConsentPanel.read(root, readControls = true) ?: return retryPanelRead()
            if (panel.controls.isEmpty() || panel.controls.any { it.name.isNullOrBlank() }) {
                return stop(R.string.log_google_inventory_unknown)
            }
            progress = Progress(panel).apply {
                val first = panel.visible.firstOrNull()?.index
                vicinity = controls.indexOfFirst { it.index == first }.coerceAtLeast(0)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Review $packageName window=${root.windowId} panel=${panel.kind} " +
                    "viewport=${panel.viewport} controls=${panel.controls.size} bodyScrollable=${panel.body.isScrollable} " +
                    "inventoryMs=${SystemClock.uptimeMillis() - inventoryStart}")
            }
        }
        panelReadRetries = 0
        val state = checkNotNull(progress)
        if (state.bodyChildCount != panel.body.childCount) {
            return stop(R.string.log_google_inventory_unknown)
        }
        val start = (state.vicinity - 8).coerceAtLeast(0)
        val end = (state.vicinity + 12).coerceAtMost(state.controls.lastIndex)
        var visible = (start..end).mapNotNull { index ->
            val control = state.controls[index]
            val row = panel.body.getChild(control.index) ?: return@mapNotNull null
            val node = row.getChild(row.childCount - 1) ?: return@mapNotNull null
            if (node.className?.toString() != "android.widget.ToggleButton" || !node.isVisibleToUser) {
                return@mapNotNull null
            }
            val bounds = Rect().also(node::getBoundsInScreen)
            if (bounds.isEmpty || !panel.viewport.contains(bounds)) return@mapNotNull null
            index to control.copy(node = node, bounds = bounds)
        }
        if (visible.isEmpty() && state.repeatedViewport >= 1) {
            val refreshed = GoogleConsentPanel.read(root, readControls = true)
                ?: return stop(R.string.log_google_panel_changed)
            visible = refreshed.visible.mapNotNull { control ->
                val index = state.controls.indexOfFirst { it.index == control.index }
                if (index < 0) null else index to control
            }
        }
        visible.firstOrNull()?.let { state.vicinity = it.first }
        val displayed = visible.map { it.second }

        if (!state.atTop) {
            if (!panel.top) return scroll(panel, state, displayed, forward = false)
            state.atTop = true
            state.lastViewport = null
            state.repeatedViewport = 0
        }
        val remaining = displayed.filter { it.index !in state.checked }
        if (!remaining.map { it.index }.containsAll(state.pendingSwitches)) {
            if (++state.unstableReads >= 3) return stop(R.string.log_google_panel_changed)
            return continueAfter(530)
        }
        if (remaining.isEmpty()) return advance(panel, state, displayed)
        inspect(panel, state, remaining, displayed)
    }

    private fun inspect(
        panel: GoogleConsentPanel,
        state: Progress,
        visible: List<GoogleConsentPanel.Control>,
        displayed: List<GoogleConsentPanel.Control>
    ) {
        val now = SystemClock.uptimeMillis()
        lastCaptureRequest?.let { previous ->
            val remaining = CAPTURE_INTERVAL_MS - (now - previous)
            if (remaining > 0) return continueAfter(remaining)
        }
        lastCaptureRequest = now
        waiting = true
        service.takeScreenshot(Display.DEFAULT_DISPLAY, { handler.post(it) },
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    screenshot.hardwareBuffer.use { buffer ->
                        if (!isCurrentSession()) return
                        val root = activeRoot() ?: return retryCaptureRead()
                        val current = GoogleConsentPanel.read(root, readControls = false)
                            ?: return retryCaptureRead()
                        if (!current.samePanel(panel)) {
                            return stop(R.string.log_google_panel_changed)
                        }
                        if (current.viewport != panel.viewport) return retryCaptureRead()
                        captureReadRetries = 0
                        val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?: return stop(R.string.log_google_capture_failed)
                        val image = try {
                            hardware.copy(Bitmap.Config.ARGB_8888, false)
                                ?: return stop(R.string.log_google_capture_failed)
                        } finally {
                            hardware.recycle()
                        }
                        try {
                            val toTurnOff = mutableListOf<GoogleConsentPanel.Control>()
                            for (control in visible.sortedBy { it.index !in state.pendingSwitches }) {
                                if (!matches(current, control)) {
                                    if (++state.unstableReads >= 3) return stop(R.string.log_google_panel_changed)
                                    return continueAfter(530)
                                }
                                state.unstableReads = 0
                                when (GoogleSwitchImage.read(image, control.bounds)) {
                                    GoogleSwitchImage.State.OFF -> {
                                        state.unknownSwitch = null
                                        state.checked.add(control.index)
                                        if (state.pendingSwitches.remove(control.index)) {
                                            ConsentActivityLog.record(
                                                packageName, R.string.log_google_switch_off,
                                                detail = control.label, subject = control.name
                                            )
                                            if (BuildConfig.DEBUG) Log.d(TAG, "Verified switch off: ${control.detail()}")
                                        }
                                    }
                                    GoogleSwitchImage.State.ON -> {
                                        state.unknownSwitch = null
                                        if (control.index in state.pendingSwitches) {
                                            if (state.pendingRechecks++ == 0) return continueAfter(410)
                                            return stop(R.string.log_google_change_failed, control.detail())
                                        }
                                        toTurnOff.add(control)
                                        if (toTurnOff.size == 2) break
                                    }
                                    GoogleSwitchImage.State.UNKNOWN -> {
                                        state.unknownReads = if (state.unknownSwitch == control.index) {
                                            state.unknownReads + 1
                                        } else 1
                                        state.unknownSwitch = control.index
                                        if (BuildConfig.DEBUG) {
                                            val age = SystemClock.uptimeMillis() - screenshot.timestamp
                                            Log.d(TAG, "Unclear switch: ${control.detail()} row=${control.index} " +
                                                "bounds=${control.bounds} attempt=${state.unknownReads} " +
                                                "screenshot=${image.width}x${image.height} ageMs=$age " +
                                                GoogleSwitchImage.describeSamples(image, control.bounds))
                                            if (state.unknownReads == 3) logRefreshedSwitch(current, control, image)
                                        }
                                        if (state.unknownReads >= 3) {
                                            return stop(R.string.log_google_state_unknown, control.detail())
                                        }
                                        return continueAfter(560)
                                    }
                                }
                            }
                            if (state.pendingSwitches.isNotEmpty()) return stop(R.string.log_google_panel_changed)
                            if (toTurnOff.isNotEmpty()) {
                                if (!tapSwitches(toTurnOff)) {
                                    return stop(R.string.log_google_change_failed, toTurnOff.first().detail())
                                }
                                state.pendingSwitches.addAll(toTurnOff.map { it.index })
                                state.pendingRechecks = 0
                                return continueAfter(410 + (toTurnOff.size - 1) * SWITCH_TAP_SPACING)
                            }
                            advance(panel, state, displayed)
                        } finally {
                            image.recycle()
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (!isCurrentSession()) return
                    if (BuildConfig.DEBUG) Log.d(TAG, "Screenshot failed: error=$errorCode")
                    if (activeRoot() == null) return retryCaptureRead()
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        continueAfter(460)
                    } else {
                        stop(R.string.log_google_capture_failed)
                    }
                }
            }
        )
    }

    private fun logRefreshedSwitch(panel: GoogleConsentPanel, control: GoogleConsentPanel.Control, image: Bitmap) {
        val row = panel.body.getChild(control.index)
        val node = row?.getChild(row.childCount - 1)
        if (node == null || !node.refresh()) {
            Log.d(TAG, "Switch refresh failed: row=${control.index}")
            return
        }
        val bounds = Rect().also(node::getBoundsInScreen)
        Log.d(TAG, "Switch refresh: row=${control.index} ${control.bounds} → $bounds " +
            "visible=${node.isVisibleToUser} enabled=${node.isEnabled} checkable=${node.isCheckable} " +
            "insideViewport=${panel.viewport.contains(bounds)} samples=${GoogleSwitchImage.describeSamples(image, bounds)}")
    }

    private fun matches(panel: GoogleConsentPanel, control: GoogleConsentPanel.Control): Boolean {
        val row = panel.body.getChild(control.index) ?: return false
        val node = row.getChild(row.childCount - 1) ?: return false
        val bounds = Rect().also(node::getBoundsInScreen)
        val matches = node.className?.toString() == "android.widget.ToggleButton" &&
            node.isEnabled && node.isVisibleToUser && bounds == control.bounds
        if (!matches && BuildConfig.DEBUG) {
            Log.d(TAG, "Switch moved: ${control.detail()} ${control.bounds} → $bounds, " +
                "enabled=${node.isEnabled}, visible=${node.isVisibleToUser}")
        }
        return matches
    }

    private fun advance(
        panel: GoogleConsentPanel,
        state: Progress,
        displayed: List<GoogleConsentPanel.Control>
    ) {
        if (state.checked.size != state.controls.size) return scroll(panel, state, displayed, forward = true)
        if (completed.add(panel.kind)) {
            val label = service.forAppLanguage().getString(
                if (panel.kind == GoogleConsentPanel.Kind.PURPOSES) R.string.google_purposes else R.string.google_vendors
            )
            ConsentActivityLog.record(packageName, R.string.log_google_panel_off, "$label · ${state.checked.size}")
            if (BuildConfig.DEBUG) Log.d(TAG, "Verified $label: ${state.checked.size} switches off")
        }
        if (completed.size == 2) {
            readyToConfirm {
                val root = activeRoot()
                val current = root?.let { GoogleConsentPanel.read(it, readControls = false) }
                if (current == null || !current.samePanel(panel) || !press(current.confirm)) {
                    ConsentActivityLog.record(packageName, R.string.log_google_panel_changed)
                    finish(R.string.result_incomplete)
                } else {
                    finish(R.string.result_google_verified)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Confirmation sent")
                }
            }
            return
        }
        if (panel.kind == GoogleConsentPanel.Kind.VENDORS) {
            if (panel.back?.let(::press) != true) return stop(R.string.log_google_panel_changed)
            return continueAfter(650)
        }
        val link = panel.vendorLink ?: return scroll(panel, state, displayed, forward = true)
        if (!press(link)) return stop(R.string.log_google_panel_changed)
        continueAfter(650)
    }

    private fun scroll(
        panel: GoogleConsentPanel,
        state: Progress,
        displayed: List<GoogleConsentPanel.Control>,
        forward: Boolean
    ) {
        if (activeRoot() == null) return
        val signature = displayed.map { it.index to it.bounds }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "${panel.kind} scroll forward=$forward top=${panel.top} " +
                "verified=${state.checked.size}/${state.controls.size} visible=$signature")
        }
        state.repeatedViewport = if (state.lastViewport == signature) state.repeatedViewport + 1 else 0
        state.lastViewport = signature
        if (state.repeatedViewport >= 3) return stop(R.string.log_google_inventory_unknown)
        // Ask the WebView to reveal a specific row. A swipe can move only the
        // compositor while the accessibility provider keeps the old bounds.
        var lookahead = false
        var revealIndex: Int? = null
        var compactAnchor: Int? = null
        val target = if (forward) {
            val next = state.controls.indexOfFirst { it.index !in state.checked }
            if (next >= 0) {
                state.vicinity = next
                val following = state.controls.getOrNull(next + 1)
                // Centering the following row can expose another switch below it.
                // Keep the first unchecked row eligible for an exact reveal if it was clipped.
                lookahead = panel.kind == GoogleConsentPanel.Kind.VENDORS &&
                    next > state.lastLookahead && state.repeatedViewport == 0 &&
                    following != null && following.index !in state.checked
                revealIndex = if (lookahead) {
                    state.lastLookahead = next
                    vendorRevealIndex(panel, state, displayed, next)
                } else next
                if (lookahead) compactAnchor = compactVendorAnchor(panel, state, displayed, next)
                compactAnchor?.let { panel.body.getChild(it) }
                    ?: panel.body.getChild(state.controls[revealIndex].index)
            } else panel.body.getChild(panel.body.childCount - 1)
        } else panel.body.getChild(0)
        val exactRetry = state.forceExactReveal && !lookahead
        state.forceExactReveal = false
        if (target != null && (state.repeatedViewport == 0 || exactRetry)) {
            val shown = target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Reveal row vicinity=${state.vicinity} target=$revealIndex " +
                    "lookahead=$lookahead accepted=$shown compactAnchor=$compactAnchor")
            }
            if (shown) {
                state.forceExactReveal = lookahead
                return continueAfter(scrollDelayMs)
            }
        }
        val bounds = panel.viewport
        val upper = bounds.top + bounds.height() * 22 / 100
        val lower = bounds.bottom - bounds.height() * 22 / 100
        val x = bounds.left + bounds.width() / 12
        // WebView can change its rendered scroll position without updating switch bounds
        // after ACTION_SCROLL_FORWARD. Use a touch gesture for the verified review.
        if (!gesture(x, if (forward) lower else upper, if (forward) upper else lower)) {
            return stop(R.string.log_google_panel_changed)
        }
        continueAfter(750)
    }

    private fun compactVendorAnchor(
        panel: GoogleConsentPanel,
        state: Progress,
        displayed: List<GoogleConsentPanel.Control>,
        next: Int
    ): Int? {
        if (displayed.size < 3 || next + 3 > state.controls.lastIndex) return null
        val first = displayed[displayed.size - 3]
        val second = displayed[displayed.size - 2]
        val third = displayed.last()
        val rowStride = second.index - first.index
        if (rowStride < 2 || third.index - second.index != rowStride) return null
        val firstGap = second.bounds.centerY() - first.bounds.centerY()
        val secondGap = third.bounds.centerY() - second.bounds.centerY()
        if (minOf(firstGap, secondGap) <= 0 || kotlin.math.abs(firstGap - secondGap) > first.bounds.height() / 3 ||
            maxOf(firstGap, secondGap) * 3 + first.bounds.height() > panel.viewport.height()
        ) return null
        val start = state.controls[next].index
        if ((1..3).any { offset -> state.controls[next + offset].index != start + offset * rowStride }) return null

        // Center a node between the middle switches so four compact rows can fit.
        // The inventory still requires a screenshot check of each switch.
        return state.controls[next + 1].index + rowStride / 2
    }

    private fun vendorRevealIndex(
        panel: GoogleConsentPanel,
        state: Progress,
        displayed: List<GoogleConsentPanel.Control>,
        next: Int
    ): Int {
        val farther = state.controls.getOrNull(next + 2) ?: return next + 1
        if (farther.index in state.checked || displayed.size < 2) return next + 1
        val first = displayed.first()
        val last = displayed.last()
        val visibleDistance = last.bounds.centerY() - first.bounds.centerY()
        if (visibleDistance <= 0) return next + 1
        val candidateDistance = visibleDistance.toLong() * (farther.index - state.controls[next].index)
        val availableAbove = (panel.viewport.height() - first.bounds.height()) / 2
        return if (candidateDistance <= availableAbove.toLong() * (last.index - first.index)) next + 2 else next + 1
    }

    private fun press(action: GoogleConsentPanel.Action): Boolean {
        val root = activeRoot() ?: return false
        val node = action.node
        if (node.windowId != root.windowId || !node.refresh() || !node.isVisibleToUser ||
            !node.isEnabled || !node.isClickable ||
            node.text?.toString()?.trim().orEmpty().ifEmpty {
                node.contentDescription?.toString()?.trim().orEmpty()
            } != action.label
        ) return false
        val bounds = Rect().also(node::getBoundsInScreen)
        return !bounds.isEmpty && gesture(bounds.centerX(), bounds.centerY())
    }

    private fun gesture(x: Int, startY: Int, endY: Int = startY): Boolean {
        if (activeRoot() == null || !canTouch(x, startY) || !canTouch(x, endY)) return false
        val path = Path().apply {
            moveTo(x.toFloat(), startY.toFloat())
            if (endY != startY) lineTo(x.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, if (endY == startY) 55 else 470)
        val callback = if (BuildConfig.DEBUG && endY != startY) {
            Log.d(TAG, "Dispatching scroll gesture x=$x y=$startY → $endY")
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Scroll gesture completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    Log.d(TAG, "Scroll gesture cancelled")
                }
            }
        } else null
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return service.dispatchGesture(gesture, callback, handler.takeIf { callback != null })
    }

    private fun tapSwitches(controls: List<GoogleConsentPanel.Control>): Boolean {
        if (activeRoot() == null) return false
        val taps = GestureDescription.Builder()
        for ((position, control) in controls.withIndex()) {
            val x = control.bounds.centerX()
            val y = control.bounds.centerY()
            if (!canTouch(x, y)) return false
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            taps.addStroke(GestureDescription.StrokeDescription(path, position * SWITCH_TAP_SPACING, 55))
        }
        if (BuildConfig.DEBUG && controls.size > 1) Log.d(TAG, "Verifying ${controls.size} switch changes together")
        return service.dispatchGesture(taps.build(), null, null)
    }

    private fun continueAfter(delay: Long) {
        waiting = true
        handler.postDelayed({
            waiting = false
            if (!isCurrentSession()) return@postDelayed
            if (Build.VERSION.SDK_INT >= 33) {
                val cleared = service.clearCache()
                if (BuildConfig.DEBUG && !cleared) Log.d(TAG, "Accessibility cache could not be cleared")
            }
            nextScan(0)
        }, delay)
    }

    private fun retryPanelRead() = retryUnavailableRead(++panelReadRetries)

    private fun retryCaptureRead() = retryUnavailableRead(++captureReadRetries)

    private fun retryUnavailableRead(attempt: Int) {
        if (!isCurrentSession()) return
        if (attempt > MAX_PANEL_READ_RETRIES) return stop(R.string.log_google_panel_changed)
        if (BuildConfig.DEBUG) Log.d(TAG, "Google panel temporarily unavailable, retry $attempt")
        continueAfter(PANEL_READ_RETRY_DELAY)
    }

    private fun stop(message: Int, detail: String? = null) {
        if (!isCurrentSession()) return
        ConsentActivityLog.record(packageName, message, detail)
        if (BuildConfig.DEBUG) {
            val text = if (detail == null) service.getString(message) else service.getString(message, detail)
            Log.d(TAG, text)
        }
        val result = when (message) {
            R.string.log_google_inventory_unknown, R.string.log_google_state_unknown,
            R.string.log_google_change_failed, R.string.log_google_panel_changed -> R.string.result_scroll_delay_review
            else -> R.string.result_vendor_review
        }
        finish(result)
    }

    private fun GoogleConsentPanel.Control.detail() = "$name · $label"

    private companion object {
        const val TAG = "CookieRejecterReview"
        const val MAX_PANEL_READ_RETRIES = 5
        const val PANEL_READ_RETRY_DELAY = 400L
        // Keep screenshot requests outside Android's rate-limit interval.
        const val CAPTURE_INTERVAL_MS = 350L
        const val SWITCH_TAP_SPACING = 115L
    }
}
