package io.github.peerless2012.ass

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes.TEXT_SSA
import androidx.media3.common.Player.Listener
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.exoplayer.ExoPlayer
import io.github.peerless2012.ass.kt.ASSTrack
import io.github.peerless2012.ass.kt.Ass
import io.github.peerless2012.ass.render.AssOverlay

@OptIn(UnstableApi::class)
class AssKeeper(val useEffectsRenderer: Boolean) : Listener {

    val ass by lazy { Ass() }
    val render by lazy { ass.createRender() }

    private var player: ExoPlayer? = null

    var track: ASSTrack? = null
        private set

    private val availableTracks = mutableMapOf<String, ASSTrack>()

    private var _videoSize = Size(0, 0)

    private var _surfaceSize = Size(0, 0)

    private var videoSizeCallback: ((Size) -> Unit)? = null

    private var surfaceSizeCallback: ((Size) -> Unit)? = null

    val videoSize: Size
        get() = _videoSize

    val surfaceSize: Size
        get() = _surfaceSize

    fun initPlayer(player: ExoPlayer) {
        player.addListener(this)
        if (useEffectsRenderer) {
            player.setVideoEffects(listOf())
        }
        this.player = player
    }

    override fun onTracksChanged(tracks: Tracks) {
        val selectedAssTrackId = getSelectedAssTrackId(tracks)
        if (selectedAssTrackId == null) {
            track = null
            if (useEffectsRenderer) {
                player?.setVideoEffects(listOf())
            }
            return
        }

        val track = availableTracks[selectedAssTrackId] ?: return
        if (this.track == track) return

        render.setTrack(track)
        if (useEffectsRenderer) {
            player?.setVideoEffects(
                listOf<Effect>(OverlayEffect(listOf(AssOverlay(render))))
            )
        }
        this.track = track
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        Log.i("AssKeeper", "onVideoSizeChanged: width = ${videoSize.width}, height = ${videoSize.height}")
        if (_videoSize.width == videoSize.width && _videoSize.height == videoSize.height) return
        _videoSize = Size(videoSize.width, videoSize.height)
        videoSizeCallback?.invoke(_videoSize)
    }

    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        super.onSurfaceSizeChanged(width, height)
        Log.i("AssKeeper", "onSurfaceSizeChanged: width = $width, height = $height")
        if (_surfaceSize.width == width && _surfaceSize.height == height) return
        _surfaceSize = Size(width, height)
        surfaceSizeCallback?.invoke(_surfaceSize)
    }

    public fun onVideoSizeChanged(callback: (Size) -> Unit) {
        this.videoSizeCallback = callback
    }

    public fun onSurfaceSizeChanged(callback: (Size) -> Unit) {
        this.surfaceSizeCallback = callback
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

    fun createTrack(format: Format): ASSTrack {
        val track = ass.createTrack()

        val header1 = format.initializationData[0].decodeToString()
        assert(header1.startsWith("Format:"))

        val header2 = format.initializationData[1].decodeToString()

        val lines = header2.lines().toMutableList()
        val index = lines.indexOfFirst {
            it.startsWith("[Events]")
        }
        if (index >= 0 && lines[index + 1].startsWith("Format:")) {
            lines[index + 1] = header1
        }
        val result = lines.joinToString(separator = "\n")
        track.readBuffer(result.toByteArray())

        availableTracks[format.id!!] = track
        return track
    }
}
