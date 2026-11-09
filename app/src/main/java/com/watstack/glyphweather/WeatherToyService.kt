package com.watstack.glyphweather

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * Glyph Toy for the Glyph Matrix. It cycles: temperature for 30 seconds, then the animated weather icon for 3 seconds,
 * then, when the icon has the sun in it, the UV index for 3 seconds; on repeat.
 *
 * Flipping the phone face down starts the cycle at the icon and refetches the weather (with UV) if it's more than
 * 10 minutes old. The SDK has no flip event, so a flip is caught two ways: the system binding the toy when Flip to
 * Glyph turns it on, and the gravity sensor going from face up to face down while it's bound.
 *
 * On Phone (4a) Pro toys only run as Always-on toys; the system sends EVENT_AOD once a minute.
 */
class WeatherToyService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var repo: WeatherRepository
    private var gm: GlyphMatrixManager? = null
    private var refreshing = false

    /** [TEMPERATURE] while the temperature is showing, 0 until FRAMES for an icon frame, or [UV] for the UV screen. */
    private var frame = TEMPERATURE
    private var lastStepAt = 0L
    private val step = Runnable { advance() }

    private var sensors: SensorManager? = null
    /** Null until the first reading, so a toy that starts face down doesn't kick off twice (binding already did). */
    private var faceDown: Boolean? = null

    private val flip = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val z = event.values[2] // m/s²: about +9.8 face up, about -9.8 face down
            when {
                z < FACE_DOWN_Z -> {
                    if (faceDown == false) kickOff()
                    faceDown = true
                }
                z > FACE_UP_Z -> faceDown = false
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            gm?.register(if (Common.is23112()) Glyph.DEVICE_23112 else Glyph.DEVICE_25111p)
            startFlipSensor()
            kickOff()
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
        stopFlipSensor()
        gm?.turnOff()
        gm?.unInit()
        gm = null
        return false
    }

    override fun onDestroy() {
        stopFlipSensor()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun startFlipSensor() {
        val sm = getSystemService(SensorManager::class.java) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        faceDown = null
        sm.registerListener(flip, sensor, SensorManager.SENSOR_DELAY_NORMAL, main)
        sensors = sm
    }

    private fun stopFlipSensor() {
        sensors?.unregisterListener(flip)
        sensors = null
    }

    /** The once-a-minute system event. If the phone slept and our timer stalled, start the cycle again. */
    private fun onTick() {
        if (SystemClock.elapsedRealtime() - lastStepAt > TEMPERATURE_MS + STALL_GRACE_MS) restartCycle()
        refreshIfOlderThan(null)
    }

    /** The phone was just flipped face down: play the icon now and make sure the weather and UV are fresh. */
    private fun kickOff() {
        main.removeCallbacks(step)
        frame = if (repo.cachedCondition() != null) 0 else TEMPERATURE
        show()
        refreshIfOlderThan(FLIP_REFRESH_MS)
    }

    /** Refetches in the background if the cache is older than [maxAgeMs], or simply stale when null. */
    private fun refreshIfOlderThan(maxAgeMs: Long?) {
        val old = if (maxAgeMs == null) repo.isStale() else repo.isStale() || repo.isOlderThan(maxAgeMs)
        if (!old || refreshing) return
        refreshing = true
        io.execute {
            repo.refresh()
            main.post {
                refreshing = false
                // Redraw a number screen now; the icon picks up a new condition on its next frame.
                if (frame == TEMPERATURE || frame == UV) draw()
            }
        }
    }

    private fun restartCycle() {
        main.removeCallbacks(step)
        frame = TEMPERATURE
        show()
    }

    /**
     * Temperature → icon frames 0‥11 → UV (only after a sun, and only if we have a UV reading) → temperature.
     * Without a weather code there's no icon, so stay on the temperature.
     */
    private fun advance() {
        val condition = repo.cachedCondition()
        frame = when {
            condition == null -> TEMPERATURE
            frame == TEMPERATURE -> 0
            frame in 0 until WeatherIcons.FRAMES - 1 -> frame + 1
            frame == WeatherIcons.FRAMES - 1 && WeatherIcons.showsSun(condition) && repo.cachedUv() != null -> UV
            else -> TEMPERATURE
        }
        show()
    }

    private fun show() {
        draw()
        lastStepAt = SystemClock.elapsedRealtime()
        val delay = when (frame) {
            TEMPERATURE -> TEMPERATURE_MS
            UV -> WeatherIcons.UV_MS
            else -> WeatherIcons.FRAME_MS
        }
        main.postDelayed(step, delay)
    }

    private fun draw() {
        val manager = gm ?: return
        val size = Common.getDeviceMatrixLength().takeIf { it > 0 } ?: 13
        val condition = repo.cachedCondition()
        val pixels = when {
            frame == UV -> lit(DigitFont.renderUv(repo.cachedUv(), size))
            frame >= 0 && condition != null -> WeatherIcons.frame(condition, frame, size)
            else -> lit(DigitFont.render(DigitFont.label(repo.cachedTemperature()), size))
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

    private fun lit(px: BooleanArray) = IntArray(px.size) { if (px[it]) WeatherIcons.FULL else 0 }

    companion object {
        private const val TEMPERATURE = -1
        private const val UV = WeatherIcons.FRAMES
        private const val TEMPERATURE_MS = 30_000L
        private const val STALL_GRACE_MS = 5_000L
        private const val FLIP_REFRESH_MS = 10 * 60 * 1000L
        private const val FACE_DOWN_Z = -8f
        private const val FACE_UP_Z = -3f
    }
}
