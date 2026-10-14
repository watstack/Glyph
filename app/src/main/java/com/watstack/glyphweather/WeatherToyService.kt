package com.watstack.glyphweather

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
import java.util.concurrent.Executors

/**
 * Glyph Toy that plays the animated weather icon on the Glyph Matrix for 5 seconds, then shows the current temperature
 * for 55 seconds, on repeat. On Phone (4a) Pro toys only run as Always-on toys; the system sends EVENT_AOD once a minute.
 */
class WeatherToyService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var repo: WeatherRepository
    private var gm: GlyphMatrixManager? = null
    private var refreshing = false

    /** -1 while the temperature is showing, else the icon animation frame on the matrix. */
    private var frame = -1
    private var lastStepAt = 0L
    private val step = Runnable { advance() }

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            gm?.register(if (Common.is23112()) Glyph.DEVICE_23112 else Glyph.DEVICE_25111p)
            restartCycle()
            refreshIfStale()
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                val event = msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                if (event == GlyphToy.EVENT_AOD || event == GlyphToy.EVENT_CHANGE) onTick()
            } else {
                super.handleMessage(msg)
            }
        }
    })

    override fun onCreate() {
        super.onCreate()
        repo = WeatherRepository(this)
    }

    override fun onBind(intent: Intent?): IBinder {
        gm = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(step)
        gm?.turnOff()
        gm?.unInit()
        gm = null
        return false
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    /**
     * The once-a-minute system event. If the phone slept and our timer stalled, start the cycle again;
     * either way refetch in the background if the weather is old.
     */
    private fun onTick() {
        if (SystemClock.elapsedRealtime() - lastStepAt > TEMPERATURE_MS + STALL_GRACE_MS) restartCycle()
        refreshIfStale()
    }

    private fun refreshIfStale() {
        if (!repo.isStale() || refreshing) return
        refreshing = true
        val hadIcon = repo.cachedCondition() != null
        io.execute {
            repo.refresh()
            main.post {
                refreshing = false
                when {
                    !hadIcon && repo.cachedCondition() != null -> restartCycle() // first weather: lead with the icon
                    frame < 0 -> draw() // show the new temperature now; the icon picks it up next time round
                }
            }
        }
    }

    /** Starts again from the icon (or the temperature, if there's no weather code yet). */
    private fun restartCycle() {
        main.removeCallbacks(step)
        frame = if (repo.cachedCondition() == null) -1 else 0
        show()
    }

    /** Icon frames 0 until [ICON_FRAMES] → temperature → icon. Without a weather code there's no icon, so stay on the temperature. */
    private fun advance() {
        frame = if (frame + 1 >= ICON_FRAMES || repo.cachedCondition() == null) -1 else frame + 1
        show()
    }

    private fun show() {
        draw()
        lastStepAt = SystemClock.elapsedRealtime()
        main.postDelayed(step, if (frame < 0) TEMPERATURE_MS else WeatherIcons.FRAME_MS)
    }

    private fun draw() {
        val manager = gm ?: return
        val size = Common.getDeviceMatrixLength().takeIf { it > 0 } ?: 13
        val condition = repo.cachedCondition()
        val pixels = if (frame >= 0 && condition != null) {
            WeatherIcons.frame(condition, frame % WeatherIcons.FRAMES, size) // the 3 s loop plays 1⅔ times
        } else {
            val lit = DigitFont.render(DigitFont.label(repo.cachedTemperature()), size)
            IntArray(lit.size) { if (lit[it]) WeatherIcons.FULL else 0 }
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (i in pixels.indices) {
            val v = pixels[i]
            bitmap.setPixel(i % size, i / size, Color.rgb(v, v, v))
        }
        val image = GlyphMatrixObject.Builder()
            .setImageSource(bitmap)
            .setPosition(0, 0)
            .build()
        val matrixFrame = GlyphMatrixFrame.Builder().addTop(image).build(this)
        try {
            manager.setMatrixFrame(matrixFrame.render())
        } catch (e: GlyphException) {
            Log.w("GlyphWeather", "Failed to draw on Glyph Matrix", e)
        }
    }

    companion object {
        private const val ICON_FRAMES = (5_000L / WeatherIcons.FRAME_MS).toInt()
        private const val TEMPERATURE_MS = 55_000L
        private const val STALL_GRACE_MS = 5_000L
    }
}
