package io.github.peerless2012.ass.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.annotation.OptIn
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import io.github.peerless2012.ass.AssKeeper
import io.github.peerless2012.ass.kt.ASSRender


@OptIn(UnstableApi::class)
class AssOverlay(
    private val assKeeper: AssKeeper
) : CanvasOverlay(true) {

    private var renderer: ASSRender? = null

    private val paint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        assKeeper.onVideoSizeChanged(VideoSize(videoSize.width, videoSize.height))
        assKeeper.onSurfaceSizeChanged(videoSize.width, videoSize.height)
        renderer = assKeeper.render
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val renderer = requireNotNull(renderer)
        val result = renderer.readFrames(presentationTimeUs / 1000)
        if (result?.changed != 0) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
        result?.images?.forEach { frame ->
            val r = frame.color shr 24 and 0xFF
            val g = frame.color shr 16 and 0xFF
            val b = frame.color shr 8 and 0xFF
            val a = 0xFF - frame.color and 0xFF
            val color = (a shl 24) or (r shl 16) or (g shl 8) or b

            paint.color = color
            canvas.drawBitmap(frame.alpha, frame.x.toFloat(), frame.y.toFloat(), paint)
        }
    }
}