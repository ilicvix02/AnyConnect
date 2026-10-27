package com.viktor.anyconnect.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.viktor.anyconnect.ai.GeminiClient
import com.viktor.anyconnect.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The accessibility service: reads the current screen and performs taps, typing
 * and scrolls so the AI agent can carry out a task inside any app.
 */
class AgentService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AgentService? = null
        const val MAX_STEPS = 18
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentState.setConnected(true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        AgentState.setConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        AgentState.setConnected(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun stop() {
        job?.cancel()
        AgentState.setRunning(false)
        AgentState.log("■ Stopped.")
        Notifier.clear(this)
    }

    fun startTask(task: String) {
        if (AgentState.running.value) return
        val prefs = Prefs(this)
        if (prefs.apiKey.isBlank()) {
            AgentState.log("⚠ No API key set. Open Settings and paste your Gemini key.")
            return
        }
        AgentState.clearLog()
        AgentState.setRunning(true)
        AgentState.log("▶ Task: $task")
        AgentState.log("  Open the app you want me to use…")
        Notifier.show(this, "Starting — open the app you want me to use")

        val gemini = GeminiClient(prefs.apiKey, prefs.model)
        job = scope.launch {
            try {
                runLoop(task, gemini)
            } catch (_: CancellationException) {
                // user stopped
            } catch (e: Exception) {
                AgentState.log("✖ Error: ${e.message}")
            } finally {
                AgentState.setRunning(false)
                Notifier.clear(this@AgentService)
            }
        }
    }

    private suspend fun runLoop(task: String, gemini: GeminiClient) {
        delay(2800) // give the user a moment to switch to the target app
        val history = StringBuilder()

        for (step in 1..MAX_STEPS) {
            if (!AgentState.running.value) break

            val screen = dumpScreen()
            Notifier.show(this, "Step $step/$MAX_STEPS — thinking…")

            val action = gemini.decideNextAction(
                task, screen.appPackage, screen.elementsText, history.toString()
            )
            if (action.thought.isNotBlank()) AgentState.log("• ${action.thought}")
            history.append("Step $step: ${action.action}")

            when (action.action) {
                "done" -> {
                    AgentState.log("✔ Done.")
                    Notifier.show(this, "Done")
                    return
                }
                "wait" -> delay(1500)
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                "scroll" -> scroll(action.direction)
                "click" -> {
                    val el = screen.elements.getOrNull(action.index)
                    if (el == null) {
                        AgentState.log("  (no element #${action.index})")
                    } else {
                        AgentState.log("  tap “${el.label.take(40)}”")
                        clickElement(el)
                    }
                    history.append(" #${action.index}")
                }
                "settext" -> {
                    val el = screen.elements.getOrNull(action.index)
                    if (el == null) {
                        AgentState.log("  (no field #${action.index})")
                    } else {
                        AgentState.log("  type “${action.text}”")
                        setText(el, action.text)
                    }
                    history.append(" #${action.index}=\"${action.text}\"")
                }
                else -> AgentState.log("  (unknown action: ${action.action})")
            }
            history.append("\n")
            delay(950) // let the screen settle before the next look
        }
        AgentState.log("⏹ Reached the step limit.")
    }

    // ---- screen reading ----

    private data class El(
        val index: Int,
        val label: String,
        val editable: Boolean,
        val clickable: Boolean,
        val bounds: Rect,
        val node: AccessibilityNodeInfo
    )

    private class Screen(val appPackage: String, val elements: List<El>) {
        val elementsText: String
            get() = elements.joinToString("\n") { e ->
                val tag = when {
                    e.editable -> "field"
                    e.clickable -> "button"
                    else -> "text"
                }
                "[${e.index}] \"${e.label.take(80)}\" ($tag)"
            }
    }

    private fun dumpScreen(): Screen {
        val root = rootInActiveWindow
        val out = ArrayList<El>()
        val pkg = root?.packageName?.toString() ?: "?"
        if (root != null) collect(root, out)
        return Screen(pkg, out)
    }

    private fun collect(node: AccessibilityNodeInfo, out: ArrayList<El>) {
        if (out.size >= 70) return
        val label = (node.text ?: node.contentDescription ?: node.hintText)
            ?.toString()?.trim().orEmpty()
        val editable = node.isEditable
        val clickable = node.isClickable
        if ((label.isNotEmpty() || editable || clickable) && node.isVisibleToUser) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) {
                val shown = if (label.isEmpty()) "(${shortClass(node.className)})" else label
                out.add(El(out.size, shown, editable, clickable, r, node))
            }
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collect(c, out)
        }
    }

    private fun shortClass(cls: CharSequence?): String {
        val s = cls?.toString() ?: "view"
        return s.substringAfterLast('.')
    }

    // ---- actions ----

    private fun clickElement(el: El) {
        var n: AccessibilityNodeInfo? = el.node
        while (n != null && !n.isClickable) n = n.parent
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
        tap(el.bounds.exactCenterX(), el.bounds.exactCenterY())
    }

    private fun setText(el: El, text: String) {
        var n: AccessibilityNodeInfo? = el.node
        while (n != null && !n.isEditable) n = n.parent
        val target = n ?: el.node
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
            )
        }
        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            tap(el.bounds.exactCenterX(), el.bounds.exactCenterY())
        }
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun scroll(direction: String) {
        val root = rootInActiveWindow
        val forward = direction != "up"
        val scrollable = root?.let { findScrollable(it) }
        val action = if (forward)
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (scrollable != null && scrollable.performAction(action)) return

        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y1: Float
        val y2: Float
        if (forward) {
            y1 = dm.heightPixels * 0.72f; y2 = dm.heightPixels * 0.28f
        } else {
            y1 = dm.heightPixels * 0.28f; y2 = dm.heightPixels * 0.72f
        }
        val path = Path().apply { moveTo(x, y1); lineTo(x, y2) }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build(),
            null, null
        )
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val found = findScrollable(c)
            if (found != null) return found
        }
        return null
    }
}
