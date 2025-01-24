package io.github.peerless2012.ass

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes.TEXT_SSA
import androidx.media3.common.Player.Listener
import androidx.media3.common.Tracks
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.exoplayer.ExoPlayer
import io.github.peerless2012.ass.kt.ASSTrack
import io.github.peerless2012.ass.kt.Ass
import io.github.peerless2012.ass.parser.AssHeaderParser
import io.github.peerless2012.ass.render.AssOverlay

@OptIn(UnstableApi::class)
class AssHandler(val useEffectsRenderer: Boolean) : Listener {

    private var player: ExoPlayer? = null

    val ass by lazy {
        Ass.newInstance()
    }

    val render by lazy {
        ass.createRender().also { render ->
            if (videoSize.isValid) {
                render.setStorageSize(videoSize.width, videoSize.height)
            }
            if (surfaceSize.isValid) {
                render.setFrameSize(surfaceSize.width, surfaceSize.height)
            }
        }
    }

    var track: ASSTrack? = null
        private set

    private val availableTracks = mutableMapOf<String, ASSTrack>()

    private var videoSize = Size(0, 0)

    var surfaceSize = Size(0, 0)
        private set

    private var surfaceSizeCallback: ((Size) -> Unit)? = null

    fun initPlayer(player: ExoPlayer) {
        player.addListener(this)
        this.player = player
        // We need to call this method to initialize the effects API as stated in Exoplayer's doc.
        setEffectsRenderer(false)
    }

    override fun onTracksChanged(tracks: Tracks) {
        val selectedAssTrackId = getSelectedAssTrackId(tracks)
        if (selectedAssTrackId == null) {
            track = null
            render.setTrack(null)
            setEffectsRenderer(false)
            return
        }

        val track = availableTracks[selectedAssTrackId] ?: return
        if (this.track == track) return

        render.setTrack(track)
        setEffectsRenderer(true)
        this.track = track
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        super.onSurfaceSizeChanged(width, height)
        Log.i("AssKeeper", "onSurfaceSizeChanged: width = $width, height = $height")
        if (surfaceSize.width == width && surfaceSize.height == height) return
        surfaceSize = Size(width, height)
        surfaceSizeCallback?.invoke(surfaceSize)
    }

    fun onSurfaceSizeChanged(callback: (Size) -> Unit) {
        this.surfaceSizeCallback = callback
    }

    fun createTrack(format: Format): ASSTrack {
        // We need to create the renderer before the tracks
        render

        val track = ass.createTrack()
        val header = AssHeaderParser.parse(format)
        track.readBuffer(header)
        availableTracks[format.id!!] = track
        return track
    }

    fun addFont(fontName: String, data: ByteArray) {
        if (Ass.isInitialized) {
            ass.addFont(fontName, data)
        }
    }

    fun setVideoSize(width: Int, height: Int) {
        videoSize = Size(width, height)
    }

    private fun getSelectedAssTrackId(tracks: Tracks): String? {
        return tracks.groups.find { group ->
            if (group.isSelected) {
                (0 until group.length).any { index ->
                    val track = group.getTrackFormat(index)
                    track.sampleMimeType == TEXT_SSA || track.codecs == TEXT_SSA
                }
            } else {
                false
            }
        }?.getTrackFormat(0)?.id
    }

    private fun setEffectsRenderer(enabled: Boolean) {
        if (!useEffectsRenderer) return

        val effects = if (enabled) {
            listOf<Effect>(OverlayEffect(listOf(AssOverlay(render))))
        } else {
            listOf()
        }
        player?.setVideoEffects(effects)
    }

    private val Size.isValid
        get() = width > 0 && height > 0
}
