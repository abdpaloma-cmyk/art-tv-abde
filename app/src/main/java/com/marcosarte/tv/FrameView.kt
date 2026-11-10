package com.marcosarte.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import kotlin.math.max
import kotlin.math.min

class FrameStyle(
    val name: String,
    val main: Int,
    val dark: Int,
    val light: Int,
    val frame: Float,
    val matte: Float,
    val matteColor: Int
)

private fun c(hex: String) = Color.parseColor(hex)

val STYLES = listOf(
    FrameStyle("Dorado clásico", c("#C9A653"), c("#574018"), c("#F5E3A1"), .046f, .040f, c("#F2EBDD")),
    FrameStyle("Nogal", c("#70472D"), c("#24160F"), c("#A77A51"), .043f, .038f, c("#EEE8DC")),
    FrameStyle("Negro museo", c("#202020"), c("#050505"), c("#666666"), .030f, .050f, Color.WHITE),
    FrameStyle("Blanco galería", c("#F0EEE8"), c("#A9A69E"), Color.WHITE, .027f, .060f, c("#FBFAF6")),
    FrameStyle("Plata cepillada", c("#BFC3C7"), c("#555B61"), c("#F4F5F6"), .037f, .040f, c("#F1EEE8")),
    FrameStyle("Roble natural", c("#A77A4E"), c("#50351F"), c("#D5B18A"), .040f, .040f, c("#F1EBDD")),
    FrameStyle("Sin marco", Color.BLACK, Color.BLACK, Color.BLACK, 0f, .018f, c("#F4F0E7"))
)

class FrameView(context: Context) : View(context) {
    var bitmap: Bitmap? = null
    var style: FrameStyle = STYLES[0]
    var infoText: String = ""
    var message: String = ""
    var fill: Boolean = false
    var infoVisible: Boolean = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c("#514B43")
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val image = bitmap
        canvas.drawColor(c("#171512"))
        if (image == null) {
            textPaint.color = c("#D0C8B9")
            textPaint.textSize = (h * .027f).coerceIn(22f, 42f)
            canvas.drawText(message.ifBlank { "Marcos de Arte" }, w / 2f, h / 2f, textPaint)
            return
        }

        val side = min(w, h)
        val frameWidth = style.frame * side
        val matteWidth = style.matte * side

        // Marco exterior con bisel y reflejos para dar profundidad.
        if (frameWidth > 0f) {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(0f, 0f, w, h,
                intArrayOf(style.light, style.main, style.dark, style.main), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            paint.color = style.dark
            canvas.drawRect(frameWidth * .10f, frameWidth * .10f,
                w - frameWidth * .10f, h - frameWidth * .10f, paint)
            paint.shader = LinearGradient(0f, h, w, 0f,
                intArrayOf(style.dark, style.main, style.light), null, Shader.TileMode.CLAMP)
            canvas.drawRect(frameWidth * .22f, frameWidth * .22f,
                w - frameWidth * .22f, h - frameWidth * .22f, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, frameWidth * .035f)
            paint.color = style.light
            canvas.drawRect(frameWidth * .35f, frameWidth * .35f,
                w - frameWidth * .35f, h - frameWidth * .35f, paint)
            paint.color = style.dark
            paint.strokeWidth = max(1f, frameWidth * .025f)
            canvas.drawRect(frameWidth * .78f, frameWidth * .78f,
                w - frameWidth * .78f, h - frameWidth * .78f, paint)
            paint.style = Paint.Style.FILL
        }

        // Cartón de paspartú.
        val mat = RectF(frameWidth, frameWidth, w - frameWidth, h - frameWidth)
        paint.color = style.matteColor
        canvas.drawRect(mat, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, side * .0012f)
        paint.color = Color.argb(48, 0, 0, 0)
        canvas.drawRect(mat, paint)
        paint.style = Paint.Style.FILL

        val artArea = RectF(mat).apply { inset(matteWidth, matteWidth) }
        if (artArea.width() <= 0f || artArea.height() <= 0f) return
        val scale = if (fill) max(artArea.width() / image.width, artArea.height() / image.height)
                   else min(artArea.width() / image.width, artArea.height() / image.height)
        val iw = image.width * scale
        val ih = image.height * scale
        val imageRect = RectF(artArea.centerX() - iw / 2f, artArea.centerY() - ih / 2f,
            artArea.centerX() + iw / 2f, artArea.centerY() + ih / 2f)

        canvas.save()
        canvas.clipRect(artArea)
        canvas.drawBitmap(image, null, imageRect, paint)
        canvas.restore()

        // Sombra y filete interior de la ventana.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, side * .001f)
        paint.color = Color.argb(65, 0, 0, 0)
        canvas.drawRect(artArea, paint)
        paint.style = Paint.Style.FILL

        // Pie de obra discreto, impreso visualmente en el paspartú.
        if (infoVisible && infoText.isNotBlank() && matteWidth > 10f) {
            textPaint.textSize = (h * .014f).coerceIn(14f, 24f)
            textPaint.color = c("#5D574F")
            val safe = TextUtils.ellipsize(infoText, textPaint, w * .58f, TextUtils.TruncateAt.END)
            val baseline = h - frameWidth - max(matteWidth * .32f, textPaint.textSize * 1.6f)
            canvas.drawText(safe.toString(), w / 2f, baseline, textPaint)
        }
    }
}
