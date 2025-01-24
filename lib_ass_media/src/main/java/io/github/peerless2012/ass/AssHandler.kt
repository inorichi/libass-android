package io.github.peerless2012.ass

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes.TEXT_SSA
import androidx.media3.common.Player.Listener
import androidx.media3.common.Tracks
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.exoplayer.ExoPlayer
import io.github.peerless2012.ass.kt.ASSRender
import io.github.peerless2012.ass.kt.ASSTrack
import io.github.peerless2012.ass.kt.Ass
import io.github.peerless2012.ass.parser.AssHeaderParser
import io.github.peerless2012.ass.render.AssOverlay

/**
 * Handles ASS (Advanced SubStation Alpha) subtitle rendering and integration with ExoPlayer.
 *
 * This class listens to ExoPlayer events and manages the creation, selection, and rendering of ASS
 * subtitle tracks.
 * @param useEffectsRenderer If true, the effects renderer is used for subtitle overlays.
 */
@OptIn(UnstableApi::class)
class AssHandler(val useEffectsRenderer: Boolean) : Listener {

    /** The ExoPlayer instance. */
    private var player: ExoPlayer? = null

    /** The ASS instance used for creating tracks and renderers. This is lazy to avoid loading
     * libass if the played media does not have ASS tracks. */
    val ass by lazy { Ass.newInstance() }

    /** The current ASS renderer. It's created as soon as a ASS track is detected. */
    var render: ASSRender? = null
        private set

    /** The currently selected ASS track. */
    var track: ASSTrack? = null
        private set

    /** The available ASS tracks in the current media. */
    private val availableTracks = mutableMapOf<String, ASSTrack>()

    /** The size of the video track. */
    private var videoSize = Size(0, 0)

    /** The size of the surface on which subtitles are rendered. */
    var surfaceSize = Size(0, 0)
        private set

    /** Callback for listening to surface size changes. */
    private var surfaceSizeCallback: ((Size) -> Unit)? = null

    /**
     * Initializes the ExoPlayer instance and attaches the listener.
     * @param player The ExoPlayer instance to attach to.
     */
    fun initPlayer(player: ExoPlayer) {
        player.addListener(this)
        this.player = player
    }

    /**
     * Handles transitions between media items in the player and resets everything to the initial
     * state.
     */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        render = null
        track = null
        availableTracks.clear()
        videoSize = Size(0, 0)
        setEffectsRenderer(false)
    }

    /**
     * Handles changes to the tracks available in the current media.
     * Configures the selected ASS track if available.
     * @param tracks The selected tracks.
     */
    override fun onTracksChanged(tracks: Tracks) {
        val selectedAssTrackId = getSelectedAssTrackId(tracks)
        if (selectedAssTrackId == null) {
            track = null
            render?.setTrack(null)
            setEffectsRenderer(false)
            return
        }

        val track = availableTracks[selectedAssTrackId] ?: return
        if (this.track == track) return

        this.track = track
        render?.setTrack(track)
        setEffectsRenderer(true)
    }

    /**
     * Handles changes to the surface size for video playback.
     * Notifies the callback if the size has changed.
     * @param width The new width of the surface.
     * @param height The new height of the surface.
     */
    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        super.onSurfaceSizeChanged(width, height)
        Log.i("AssHandler", "onSurfaceSizeChanged: width = $width, height = $height")
        if (surfaceSize.width == width && surfaceSize.height == height) return
        surfaceSize = Size(width, height)
        surfaceSizeCallback?.invoke(surfaceSize)
    }

    /**
     * Sets a callback to handle changes to the surface size.
     * @param callback The callback function to invoke when the surface size changes.
     */
    fun onSurfaceSizeChanged(callback: (Size) -> Unit) {
        this.surfaceSizeCallback = callback
    }

    /**
     * Updates the video size for the ASS renderer. Called as soon as the video size is known in
     * order to properly render subtitles.
     * @param width The width of the video.
     * @param height The height of the video.
     */
    fun setVideoSize(width: Int, height: Int) {
        videoSize = Size(width, height)
    }

    /**
     * Creates a new ASS track from the given format and saves it in the [availableTracks].
     * The renderer and libass are also created if needed.
     * @param format The format of the ASS track.
     * @return The created ASS track.
     */
    fun createTrack(format: Format): ASSTrack {
        // Ensure the renderer is created before creating tracks.
        createRenderIfNeeded()

        val track = ass.createTrack()
        val header = AssHeaderParser.parse(format)
        track.readBuffer(header)
        availableTracks[format.id!!] = track
        return track
    }

    /**
     * Ensures the ASS renderer is created if it does not already exist.
     */
    private fun createRenderIfNeeded() {
        if (render != null) return
        render = ass.createRender().also { render ->
            if (videoSize.isValid) {
                render.setStorageSize(videoSize.width, videoSize.height)
            }
            if (surfaceSize.isValid) {
                render.setFrameSize(surfaceSize.width, surfaceSize.height)
            }
        }
    }

    /**
     * Retrieves the ID of the selected ASS track, if any.
     * @param tracks The selected tracks.
     * @return The ID of the selected ASS track, or null if none.
     */
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

    /**
     * Configures the effects renderer for subtitle overlays.
     * @param enabled If true, enables the effects renderer; otherwise, disables it.
     */
    private fun setEffectsRenderer(enabled: Boolean) {
        if (!useEffectsRenderer) return

        val effects = if (enabled) {
            val render = render ?: return
            listOf<Effect>(OverlayEffect(listOf(AssOverlay(render))))
        } else {
            listOf()
        }
        player?.setVideoEffects(effects)
    }

    /**
     * Checks if the size is valid (both width and height are greater than 0).
     */
    private val Size.isValid
        get() = width > 0 && height > 0
}