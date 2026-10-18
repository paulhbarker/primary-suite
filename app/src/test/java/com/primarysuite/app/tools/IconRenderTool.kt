package com.primarysuite.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.primarysuite.app.R
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the adaptive launcher icon's vector drawables to PNG with Android's
 * own renderer. Opt-in: does nothing unless scripts/icon/render.sh created
 * [OUT]/REQUEST. The script then copies the PNGs into place.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconRenderTool {

    private val out = File(OUT)
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The full 108 dp layer stack at [size] px. */
    private fun layers(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach {
            ContextCompat.getDrawable(context, it)!!.apply { setBounds(0, 0, size, size); draw(canvas) }
        }
        return bitmap
    }

    /** What a launcher shows: the central 72 of 108, masked to [shape], at [size] px, inset by [inset] px. */
    private fun masked(size: Int, shape: (RectF) -> Path, inset: Float = 0f): Bitmap {
        val full = layers((size * 108f / 72f).toInt() + 1)
        val crop = (full.width - full.width * 72f / 108f) / 2f
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val rect = RectF(inset, inset, size - inset, size - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(shape(rect), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val scale = (size - 2 * inset) / (full.width - 2 * crop)
        canvas.save()
        canvas.translate(inset, inset)
        canvas.scale(scale, scale)
        canvas.drawBitmap(full, -crop, -crop, paint)
        canvas.restore()
        return result
    }

    private val circle = { r: RectF -> Path().apply { addOval(r, Path.Direction.CW) } }
    private val rounded = { r: RectF -> Path().apply { addRoundRect(r, r.width() * 0.22f, r.width() * 0.22f, Path.Direction.CW) } }
    private val squircle = { r: RectF ->
        Path().apply {
            // Superellipse (n = 4), as on Pixel and Samsung launchers.
            val cx = r.centerX(); val cy = r.centerY(); val a = r.width() / 2
            for (i in 0..360) {
                val t = Math.toRadians(i.toDouble())
                val c = Math.cos(t); val s = Math.sin(t)
                val x = cx + a * Math.signum(c) * Math.pow(Math.abs(c), 0.5)
                val y = cy + a * Math.signum(s) * Math.pow(Math.abs(s), 0.5)
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        }
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun render() {
        assumeTrue("run scripts/icon/render.sh", File(out, "REQUEST").exists())

        // Legacy launcher PNGs (API 23-25), Material keylines: 44 of 48 for the square, full for round.
        mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192).forEach { (dpi, px) ->
            File(out, "mipmap-$dpi").mkdirs()
            save(masked(px, rounded, inset = px / 24f), "mipmap-$dpi/ic_launcher.png")
            save(masked(px, circle, inset = px / 48f), "mipmap-$dpi/ic_launcher_round.png")
        }
        save(masked(192, rounded), "icon.png")       // install page
        save(masked(512, rounded), "icon-512.png")   // store-style, full size

        // A mockup sheet: the three common masks at launcher size and small, on a wallpaper-ish grey.
        val sheet = Bitmap.createBitmap(3 * 220 + 40, 220 + 120, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(Color.rgb(0x2b, 0x2d, 0x31))
            listOf(circle, squircle, rounded).forEachIndexed { i, shape ->
                drawBitmap(masked(192, shape), 20f + i * 220 + 14, 20f, null)
                drawBitmap(masked(48, shape), 20f + i * 220 + 86, 240f, null)
            }
        }
        save(sheet, "mockup.png")
        save(layers(432), "layers.png")
    }

    companion object {
        const val OUT = "/tmp/primary-suite-icon"
    }
}
