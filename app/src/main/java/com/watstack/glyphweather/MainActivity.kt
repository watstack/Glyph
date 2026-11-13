package com.watstack.glyphweather

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * One-screen setup: a preview of the icon, location permission, °C/°F, planes on/off with the last plane seen,
 * and a shortcut to Nothing's Glyph Toys manager.
 */
class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var repo: WeatherRepository
    private lateinit var flights: FlightRepository
    private lateinit var status: TextView
    private lateinit var lastPlane: TextView
    private lateinit var preview: MatrixPreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = WeatherRepository(this)
        flights = FlightRepository(this)

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

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            listOf(preview, status, unit, planes, lastPlane, activate, help).forEach { addView(it) }
        })
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
        preview.condition = condition
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
    }
}
