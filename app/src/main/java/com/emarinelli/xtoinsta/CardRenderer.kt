package com.emarinelli.xtoinsta

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/** The post drawn as a 9:16 image, with a transparent hole where the video plays. */
class RenderedCard(val bitmap: Bitmap, val videoRect: RectF)

object CardRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1920

    private const val MARGIN = 56f
    private const val PAD = 44f
    private const val AVATAR = 112f
    private const val GAP = 32f
    private const val CORNER = 28f
    private const val MIN_VIDEO_SHARE = 0.42f

    private val BACKGROUND = Color.rgb(0, 0, 0)
    private val PANEL = Color.rgb(22, 24, 28)
    private val TEXT = Color.rgb(231, 233, 234)
    private val MUTED = Color.rgb(113, 118, 123)

    fun render(tweet: Tweet, avatar: Bitmap?, videoWidth: Int, videoHeight: Int): RenderedCard {
        val namePaint = textPaint(46f, TEXT, bold = true)
        val handlePaint = textPaint(38f, MUTED)
        val bodyPaint = textPaint(44f, TEXT)
        val footerPaint = textPaint(32f, MUTED)

        val panelLeft = MARGIN
        val panelRight = WIDTH - MARGIN
        val contentWidth = panelRight - panelLeft - 2 * PAD
        val aspect = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight else 16f / 9f

        val headerH = AVATAR
        val footerH = footerPaint.fontSpacing
        val fixedH = 2 * MARGIN + 2 * PAD + headerH + footerH + 3 * GAP
        val fullWidthVideoH = contentWidth / aspect

        // Give the text whatever room is left once the video has a fair share of the frame.
        val minVideoH = minOf(fullWidthVideoH, HEIGHT * MIN_VIDEO_SHARE)
        val lineH = bodyPaint.fontSpacing
        val maxLines = ((HEIGHT - fixedH - minVideoH) / lineH).toInt().coerceAtLeast(1)
        val body = if (tweet.text.isBlank()) null else StaticLayout.Builder
            .obtain(tweet.text, 0, tweet.text.length, bodyPaint, contentWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val bodyH = body?.height?.toFloat() ?: 0f
        val bodyGap = if (body != null) GAP else 0f

        val maxVideoH = HEIGHT - fixedH - bodyH - bodyGap + GAP
        var videoH = fullWidthVideoH
        var videoW = contentWidth
        if (videoH > maxVideoH) {
            videoH = maxVideoH
            videoW = videoH * aspect
        }

        val panelH = 2 * PAD + headerH + GAP + bodyH + bodyGap + videoH + GAP + footerH
        val panelTop = (HEIGHT - panelH) / 2
        val panel = RectF(panelLeft, panelTop, panelRight, panelTop + panelH)

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        canvas.drawRoundRect(panel, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL })

        // Header: avatar, name and handle.
        val x = panelLeft + PAD
        var y = panelTop + PAD
        drawAvatar(canvas, avatar, x, y)
        val textX = x + AVATAR + 28f
        canvas.drawText(ellipsize(tweet.authorName, namePaint, panelRight - PAD - textX), textX, y + 50f, namePaint)
        canvas.drawText("@${tweet.authorHandle}", textX, y + 100f, handlePaint)
        y += headerH + GAP

        if (body != null) {
            canvas.save()
            canvas.translate(x, y)
            body.draw(canvas)
            canvas.restore()
            y += bodyH + bodyGap
        }

        val videoLeft = x + (contentWidth - videoW) / 2
        val videoRect = RectF(videoLeft, y, videoLeft + videoW, y + videoH)
        canvas.drawRoundRect(videoRect, CORNER, CORNER, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        })
        y += videoH + GAP

        canvas.drawText(tweet.permalink, x, y + footerPaint.textSize, footerPaint)

        return RenderedCard(bitmap, videoRect)
    }

    private fun drawAvatar(canvas: Canvas, avatar: Bitmap?, x: Float, y: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = AVATAR / 2
        if (avatar == null) {
            paint.color = MUTED
            canvas.drawCircle(x + r, y + r, r, paint)
            return
        }
        val scaled = Bitmap.createScaledBitmap(avatar, AVATAR.toInt(), AVATAR.toInt(), true)
        paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.translate(x, y)
        canvas.drawCircle(r, r, r, paint)
        canvas.restore()
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
}
