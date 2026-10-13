package com.hsrp.dealer.debug

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * DEBUG BUILDS ONLY (app/src/debug). Lets the scripted simulator (`pnpm sim` in hsrp-dms-be) read what is on
 * screen in milliseconds instead of the ~3 s an `uiautomator dump` takes. Answers `GET /nodes` on 127.0.0.1:7777
 * inside the emulator (reach it with `adb forward tcp:7777 tcp:7777`) with the visible Compose semantics nodes
 * and their screen bounds. It only reads; taps and typing still go through `adb shell input`, so they are real
 * touches. Release builds do not contain this class.
 */
object AutomationBridge {
    private const val PORT = 7777
    @Volatile private var current: Activity? = null
    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun start(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { current = activity }
            override fun onActivityPaused(activity: Activity) { if (current === activity) current = null }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        thread(name = "automation-bridge", isDaemon = true) {
            try {
                ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1")).use { server ->
                    while (true) server.accept().use { socket ->
                        val request = socket.getInputStream().bufferedReader().readLine() ?: ""
                        val body = if (request.startsWith("GET /nodes")) snapshot() else "{\"ok\":true}"
                        val bytes = body.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AutomationBridge", "stopped: ${e.message}")
            }
        }
    }

    /** Reads the semantics tree on the main thread and returns it as JSON. */
    private fun snapshot(): String {
        var json = "{\"activity\":null,\"nodes\":[]}"
        val done = CountDownLatch(1)
        main.post {
            try {
                val activity = current
                if (activity != null) {
                    val decor = activity.window.decorView
                    val origin = IntArray(2).also { decor.getLocationOnScreen(it) }
                    val out = StringBuilder()
                    findRoots(decor).forEach { root -> walk(root.semanticsOwner.rootSemanticsNode, origin, out) }
                    json = "{\"activity\":${q(activity.javaClass.simpleName)},\"width\":${decor.width},\"height\":${decor.height},\"nodes\":[${out.removeSuffix(",")}]}"
                }
            } finally {
                done.countDown()
            }
        }
        done.await(2, TimeUnit.SECONDS)
        return json
    }

    private fun findRoots(view: View): List<ViewRootForTest> =
        if (view is ViewRootForTest) listOf(view)
        else if (view is ViewGroup) (0 until view.childCount).flatMap { findRoots(view.getChildAt(it)) }
        else emptyList()

    private fun walk(node: SemanticsNode, origin: IntArray, out: StringBuilder) {
        val c = node.config
        val text = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val edit = c.getOrNull(SemanticsProperties.EditableText)?.text
        val desc = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val clickable = c.contains(SemanticsActions.OnClick)
        val toggle = c.getOrNull(SemanticsProperties.ToggleableState)
        // Unclipped position and size, so the simulator also sees nodes scrolled out of view and can scroll to them.
        val pos = node.positionInWindow
        val w = node.size.width
        val h = node.size.height
        if ((text != null || edit != null || desc != null || clickable) && w > 0 && h > 0) {
            out.append("{\"text\":${q(text)},\"value\":${q(edit)},\"desc\":${q(desc)},")
                .append("\"x\":${(pos.x + origin[0]).toInt()},\"y\":${(pos.y + origin[1]).toInt()},\"w\":$w,\"h\":$h,")
                .append("\"clickable\":$clickable,\"editable\":${edit != null},\"enabled\":${!c.contains(SemanticsProperties.Disabled)},")
                .append("\"checked\":${toggle == ToggleableState.On},\"selected\":${c.getOrNull(SemanticsProperties.Selected) == true}},")
        }
        node.children.forEach { walk(it, origin, out) }
    }

    private fun q(s: String?): String =
        if (s == null) "null"
        else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""
}
