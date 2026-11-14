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
import android.os.PowerManager
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
 * Glyph Toy for the Glyph Matrix. What it shows, step by step, is [ToySequence]: temperature for 30 seconds, the
 * animated weather icon for 3 seconds, then the UV index after a sun; on repeat.
 *
 * Flipping the phone face down starts the cycle at the icon and refetches the weather (with UV) if it's more than
 * 10 minutes old. The SDK has no flip event, so a flip is caught two ways: the system binding the toy when Flip to
 * Glyph turns it on, and the gravity sensor going from face up to face down while it's bound.
 *
 * When a plane flies within 5 km, it interrupts: the plane crosses the matrix on its real heading, its flight number,
 * route and type scroll past, then its altitude shows, and the weather cycle starts again. Flights are polled every
 * 15 seconds while the phone is face down, and each plane is shown once per pass. The CPU is kept awake for the
 * plane sequence; without that the phone sleeps after the crossing and the rest never shows.
 *
 * The app can send a simulated weather or plane through [Simulation]; it plays now if face down, or on the next flip.
 *
 * On Phone (4a) Pro toys only run as Always-on toys; the system sends EVENT_AOD once a minute.
 */
class WeatherToyService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var repo: WeatherRepository
    private lateinit var flights: FlightRepository
    private lateinit var seq: ToySequence
    private var gm: GlyphMatrixManager? = null
    private var refreshing = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastStepAt = 0L
    private val step = Runnable { advance() }

    private var polling = false
    private val poll = object : Runnable {
        override fun run() {
            pollFlights()
            main.postDelayed(this, FLIGHT_POLL_MS)
        }
    }
    /** When each plane (by ICAO hex) was last shown, so one pass only interrupts once. */
    private val shownAt = HashMap<String, Long>()

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
            // Play a simulation now unless the phone is known to be face up (the matrix can't be seen then).
            Simulation.listener = { request ->
                if (faceDown == false) {
                    false
                } else {
                    play(request)
                    true
                }
            }
            kickOff()
            main.removeCallbacks(poll)
            main.postDelayed(poll, FLIGHT_POLL_MS)
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
        flights = FlightRepository(this)
        seq = ToySequence({ WeatherState(repo.cachedTemperature(), repo.cachedCondition(), repo.cachedUv()) })
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlyphSky:plane")
            ?.apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder {
        gm = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Simulation.listener = null
        main.removeCallbacks(step)
        main.removeCallbacks(poll)
        seq.restart(atIcon = false)
        releaseWake()
        stopFlipSensor()
        gm?.turnOff()
        gm?.unInit()
        gm = null
        return false
    }

    override fun onDestroy() {
        stopFlipSensor()
        releaseWake()
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
        val stalledFor = SystemClock.elapsedRealtime() - lastStepAt
        if (stalledFor > ToySequence.TEMPERATURE_MS + STALL_GRACE_MS) {
            if (seq.isPlane) Log.w(TAG, "Plane sequence stalled in ${seq.phase} for ${stalledFor / 1000} s")
            restartCycle()
        }
        refreshIfOlderThan(null)
        pollFlights()
    }

    /** The phone was just flipped face down: play a waiting simulation, or the icon now; make sure weather is fresh. */
    private fun kickOff() {
        val pending = Simulation.take(SystemClock.elapsedRealtime())
        if (pending != null) {
            play(pending)
        } else {
            main.removeCallbacks(step)
            seq.restart(atIcon = true)
            releaseWake()
            show()
        }
        refreshIfOlderThan(FLIP_REFRESH_MS)
        pollFlights()
    }

    private fun play(request: Simulation.Request) {
        Log.i(TAG, "Simulating $request")
        when (request) {
            is Simulation.Plane -> startPlane(request.flight)
            is Simulation.Weather -> {
                main.removeCallbacks(step)
                releaseWake()
                seq.simulateWeather(request.state, repeat = false)
                show()
            }
        }
    }

    /** Looks for a plane overhead in the background; a new one interrupts whatever is showing. */
    private fun pollFlights() {
        if (polling || seq.isPlane || faceDown == false || !flights.enabled) return
        polling = true
        io.execute {
            val found = flights.nearest()
            main.post {
                polling = false
                if (found != null && gm != null && !seq.isPlane && isNew(found)) startPlane(found)
            }
        }
    }

    /** Whether [f] hasn't been shown in the last [PLANE_REPEAT_MS]; marks it shown. */
    private fun isNew(f: Flight): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = shownAt[f.hex]
        if (last != null && now - last < PLANE_REPEAT_MS) return false
        shownAt.entries.removeAll { now - it.value > PLANE_REPEAT_MS }
        shownAt[f.hex] = now
        return true
    }

    private fun startPlane(f: Flight) {
        Log.i(TAG, "Plane: ${f.ticker()} at ${f.altitudeFt} ft, ${"%.1f".format(f.distanceKm)} km")
        main.removeCallbacks(step)
        seq.startPlane(f, matrixSize())
        // Keep the CPU awake until the sequence ends, or the timer freezes once the phone sleeps.
        wakeLock?.acquire(seq.planeDurationMs() + WAKE_MARGIN_MS)
        show()
    }

    private fun releaseWake() {
        wakeLock?.takeIf { it.isHeld }?.release()
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
                if (seq.phase == ToySequence.Phase.TEMPERATURE || seq.phase == ToySequence.Phase.UV) draw()
            }
        }
    }

    private fun restartCycle() {
        main.removeCallbacks(step)
        seq.stopSimulation()
        seq.restart(atIcon = false)
        releaseWake()
        show()
    }

    private fun advance() {
        val before = seq.phase
        seq.advance()
        val after = seq.phase
        if (before != after && (before.isPlane() || after.isPlane())) Log.i(TAG, "Plane sequence: $before → $after")
        if (before.isPlane() && !seq.isPlane) releaseWake()
        show()
    }

    private fun ToySequence.Phase.isPlane() =
        this == ToySequence.Phase.CROSSING || this == ToySequence.Phase.SCROLL || this == ToySequence.Phase.ALTITUDE

    private fun show() {
        draw()
        lastStepAt = SystemClock.elapsedRealtime()
        main.postDelayed(step, seq.delayMs())
    }

    private fun draw() {
        val manager = gm ?: return
        val size = matrixSize()
        val pixels = seq.pixels(size)
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
            Log.w(TAG, "Failed to draw on Glyph Matrix", e)
        }
    }

    private fun matrixSize() = Common.getDeviceMatrixLength().takeIf { it > 0 } ?: 13

    companion object {
        private const val TAG = "GlyphSky"
        private const val STALL_GRACE_MS = 5_000L
        /** Slack on top of a plane sequence's length before the wake lock lets go by itself. */
        private const val WAKE_MARGIN_MS = 10_000L
        private const val FLIP_REFRESH_MS = 10 * 60 * 1000L
        private const val FACE_DOWN_Z = -8f
        private const val FACE_UP_Z = -3f
        private const val FLIGHT_POLL_MS = 15_000L
        /** A plane already shown isn't shown again for this long (it's still the same pass overhead). */
        private const val PLANE_REPEAT_MS = 10 * 60 * 1000L
    }
}
