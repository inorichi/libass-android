package io.github.peerless2012.ass.render

import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.TextureOverlay
import io.github.peerless2012.ass.AssKeeper
import io.github.peerless2012.ass.kt.ASSRender
import java.nio.ByteBuffer


@OptIn(UnstableApi::class)
class AssOverlay(
    private val assKeeper: AssKeeper
) : TextureOverlay() {

    private var textureId = C.INDEX_UNSET
    private lateinit var size: Size
    private lateinit var renderer: ASSRender

    override fun getTextureId(presentationTimeUs: Long): Int {
        if (textureId == C.INDEX_UNSET) {
            textureId = generateTexture()
            renderer = assKeeper.render
        }
//        println("+++ render ${Thread.currentThread()}")
        // TODO syncing for now to avoid crash with AssParser, we should avoid it
        synchronized("") {
            renderer.renderFrame(textureId, presentationTimeUs)
        }
        return textureId
    }

    override fun getTextureSize(presentationTimeUs: Long): Size {
        return size
    }

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        size = videoSize
    }

    private fun generateTexture(): Int {
        val textureId = GlUtil.generateTexture()
        GlUtil.bindTexture(GLES20.GL_TEXTURE_2D, textureId, GLES20.GL_LINEAR)
        val emptyBuffer = ByteBuffer.allocateDirect(size.width * size.height * 4) // RGBA format

        (0 until 1920*20).forEach {
            emptyBuffer.asLongBuffer().put(it, 0xFFFFFFFF)
        }

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size.width, size.height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, emptyBuffer
        )
        return textureId
    }

    override fun release() {
        super.release()
        if (textureId != C.INDEX_UNSET) {
            GlUtil.deleteTexture(textureId)
            textureId = C.INDEX_UNSET
        }
    }
}
