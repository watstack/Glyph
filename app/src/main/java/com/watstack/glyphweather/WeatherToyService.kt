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
 * Glyph Toy that shows the current temperature on the Glyph Matrix.
 * On Phone (4a) Pro toys only run as Always-on toys; the system sends EVENT_AOD once a minute.
 */
class WeatherToyService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var repo: WeatherRepository
    private var gm: GlyphMatrixManager? = null
    private var refreshing = false

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName) {
            gm?.register(if (Common.is23112()) Glyph.DEVICE_23112 else Glyph.DEVICE_25111p)
            tick()
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                val event = msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                if (event == GlyphToy.EVENT_AOD || event == GlyphToy.EVENT_CHANGE) tick()
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
        gm?.turnOff()
        gm?.unInit()
        gm = null
        return false
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    /** Draw what we have now; refetch in the background if it's old. */
    private fun tick() {
        draw()
        if (repo.isStale() && !refreshing) {
            refreshing = true
            io.execute {
                repo.refresh()
                main.post {
                    refreshing = false
                    draw()
                }
            }
        }
    }

    private fun draw() {
        val manager = gm ?: return
        val size = Common.getDeviceMatrixLength().takeIf { it > 0 } ?: 13
        val pixels = DigitFont.render(DigitFont.label(repo.cachedTemperature()), size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (i in pixels.indices) {
            bitmap.setPixel(i % size, i / size, if (pixels[i]) Color.WHITE else Color.BLACK)
        }
        val image = GlyphMatrixObject.Builder()
            .setImageSource(bitmap)
            .setPosition(0, 0)
            .build()
        val frame = GlyphMatrixFrame.Builder().addTop(image).build(this)
        try {
            manager.setMatrixFrame(frame.render())
        } catch (e: GlyphException) {
            Log.w("GlyphWeather", "Failed to draw on Glyph Matrix", e)
        }
    }
}
