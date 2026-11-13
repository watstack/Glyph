package com.watstack.glyphweather

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * One-screen setup: a live preview of the matrix, location permission, °C/°F, planes on/off with the last plane seen,
 * and a shortcut to Nothing's Glyph Toys manager.
 *
 * Simulate: pick any weather, or find the nearest real plane (within 50 km), to watch it in the preview; then
 * "Show on Glyph" plays the same thing on the real matrix.
 */
class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var repo: WeatherRepository
    private lateinit var flights: FlightRepository
    private lateinit var status: TextView
    private lateinit var lastPlane: TextView
    private lateinit var preview: MatrixPreviewView
    private lateinit var simStatus: TextView
    private lateinit var findPlane: Button

    /** Drives the preview: the same sequence the toy plays, with the temperature screen shortened. */
    private lateinit var seq: ToySequence
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            seq.advance()
            preview.pixels = seq.pixels(preview.size)
            main.postDelayed(this, seq.delayMs())
        }
    }
    /** What "Show on Glyph" sends: the weather picked or the plane found last, or null for live weather. */
    private var simulation: Simulation.Request? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = WeatherRepository(this)
        flights = FlightRepository(this)
        seq = ToySequence({ liveWeather() }, temperatureMs = PREVIEW_TEMPERATURE_MS)

        val pad = (24 * resources.displayMetrics.density).toInt()
        preview = MatrixPreviewView(this)
        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val unit = Switch(this).apply {
            text = getString(R.string.use_fahrenheit)
            isChecked = repo.fahrenheit
            setOnCheckedChangeListener { _, checked ->
                repo.fahrenheit = checked
                refresh()
            }
        }
        val planes = Switch(this).apply {
            text = getString(R.string.show_planes)
            isChecked = flights.enabled
            setOnCheckedChangeListener { _, checked -> flights.enabled = checked }
        }
        lastPlane = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, pad / 3, 0, pad / 3)
        }
        val activate = Button(this).apply {
            text = getString(R.string.activate_toy)
            setOnClickListener { openToysManager() }
        }
        val help = TextView(this).apply { text = getString(R.string.setup_help) }

        val simTitle = TextView(this).apply {
            text = getString(R.string.simulate)
            textSize = 18f
            setPadding(0, pad, 0, pad / 3)
        }
        val weatherPicker = Spinner(this).apply {
            val names = listOf(getString(R.string.live_weather)) + WeatherIcons.Condition.entries.map(::conditionName)
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, names)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    pickWeather(WeatherIcons.Condition.entries.getOrNull(position - 1))
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        findPlane = Button(this).apply {
            text = getString(R.string.find_plane)
            setOnClickListener { findNearestPlane() }
        }
        val showOnGlyph = Button(this).apply {
            text = getString(R.string.show_on_glyph)
            setOnClickListener { showOnGlyph() }
        }
        simStatus = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, pad / 3, 0, pad)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
            setPadding(0, 0, 0, pad / 2)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            listOf(
                preview, status, unit, planes, lastPlane,
                simTitle, weatherPicker, findPlane, showOnGlyph, simStatus,
                activate, help,
            ).forEach { addView(it) }
        }
        // Android 15 draws apps edge to edge: keep the content clear of the status bar, camera cutout and nav bar.
        setContentView(ScrollView(this).apply {
            addView(content)
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                content.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
                insets
            }
        })
    }

    override fun onPause() {
        main.removeCallbacks(tick)
        super.onPause()
    }

    /** Restarts the preview from the current step of [seq]. */
    private fun replay() {
        main.removeCallbacks(tick)
        preview.pixels = seq.pixels(preview.size)
        main.postDelayed(tick, seq.delayMs())
    }

    private fun liveWeather() = WeatherState(repo.cachedTemperature(), repo.cachedCondition(), repo.cachedUv())

    /** A simulated [condition], or back to live weather for null. Uses the live temperature and UV when known. */
    private fun pickWeather(condition: WeatherIcons.Condition?) {
        if (condition == null) {
            simulation = null
            seq.stopSimulation()
            seq.restart(atIcon = true)
        } else {
            val state = WeatherState(repo.cachedTemperature() ?: SAMPLE_TEMPERATURE, condition, repo.cachedUv() ?: SAMPLE_UV)
            simulation = Simulation.Weather(state)
            seq.simulateWeather(state, repeat = true)
            simStatus.text = getString(R.string.simulating_weather, conditionName(condition))
        }
        replay()
    }

    private fun conditionName(c: WeatherIcons.Condition): String = when (c) {
        WeatherIcons.Condition.CLEAR_DAY, WeatherIcons.Condition.PARTLY_CLOUDY_DAY -> getString(R.string.by_day, c.label)
        WeatherIcons.Condition.CLEAR_NIGHT, WeatherIcons.Condition.PARTLY_CLOUDY_NIGHT -> getString(R.string.at_night, c.label)
        else -> c.label
    }

    /** Fetches the nearest real plane within [SIMULATE_RADIUS_KM] and plays it in the preview. */
    private fun findNearestPlane() {
        findPlane.isEnabled = false
        simStatus.text = getString(R.string.finding_plane)
        io.execute {
            val lookup = flights.find(SIMULATE_RADIUS_KM)
            runOnUiThread {
                findPlane.isEnabled = true
                val f = lookup.flight
                simStatus.text = when {
                    f != null -> getString(R.string.found_plane, flights.describe(f))
                    !lookup.hasLocation -> getString(R.string.need_location)
                    !lookup.reached -> getString(R.string.flight_feed_unreachable)
                    else -> getString(R.string.no_planes_near, SIMULATE_RADIUS_KM.toInt())
                }
                if (f != null) {
                    simulation = Simulation.Plane(f)
                    seq.startPlane(f, preview.size)
                    replay()
                }
            }
        }
    }

    /** Sends the current simulation to the real matrix. */
    private fun showOnGlyph() {
        val request = simulation ?: run {
            Toast.makeText(this, R.string.pick_something_first, Toast.LENGTH_SHORT).show()
            return
        }
        val now = Simulation.send(request, SystemClock.elapsedRealtime())
        Toast.makeText(this, if (now) R.string.playing_on_glyph else R.string.flip_to_show, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            updateLocationThenRefresh()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
        showTemperature()
        showLastPlane()
        replay()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            updateLocationThenRefresh()
        } else if (requestCode == REQ_LOCATION) {
            status.text = getString(R.string.need_location)
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    /** While the app is open (foreground) Android will give us a fresh approximate fix. */
    private fun updateLocationThenRefresh() {
        val lm = getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) }
        if (provider == null) {
            refresh()
            return
        }
        try {
            lm.getCurrentLocation(provider, null, mainExecutor) { location ->
                location?.let(repo::saveLocation)
                refresh()
            }
        } catch (e: SecurityException) {
            refresh()
        }
    }

    private fun refresh() {
        status.text = getString(R.string.loading)
        io.execute {
            val ok = repo.refresh()
            runOnUiThread {
                showTemperature()
                if (!ok && repo.cachedTemperature() == null) status.text = getString(R.string.no_weather)
            }
        }
    }

    private fun showTemperature() {
        val t = repo.cachedTemperature() ?: return
        val unit = if (repo.fahrenheit) "F" else "C"
        val condition = repo.cachedCondition()
        val weather = if (condition == null) {
            getString(R.string.current_temp, DigitFont.label(t), unit)
        } else {
            getString(R.string.current_weather, DigitFont.label(t), unit, condition.label)
        }
        val uv = repo.cachedUv()
        status.text = if (uv == null) weather else "$weather · UV ${Math.round(uv)}"
    }

    private fun showLastPlane() {
        val (text, at) = flights.lastSeen() ?: run {
            lastPlane.text = getString(R.string.no_planes_yet)
            return
        }
        val ago = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        lastPlane.text = getString(R.string.last_plane, text, ago)
    }

    private fun openToysManager() {
        val intent = Intent().setComponent(
            ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toys_manager_missing, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQ_LOCATION = 1
        /** The preview's temperature screen; the matrix holds it for 30 s, which is too long to watch. */
        private const val PREVIEW_TEMPERATURE_MS = 3_000L
        private const val SIMULATE_RADIUS_KM = 50.0
        private const val SAMPLE_TEMPERATURE = 22.0
        private const val SAMPLE_UV = 7.0
    }
}
