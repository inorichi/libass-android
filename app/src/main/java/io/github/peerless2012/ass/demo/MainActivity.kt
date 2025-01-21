package io.github.peerless2012.ass.demo

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.effect.OverlayEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import io.github.peerless2012.ass.AssKeeper
import io.github.peerless2012.ass.extractor.withAssMkvSupport
import io.github.peerless2012.ass.factory.AssSubtitleParserFactory
import io.github.peerless2012.ass.render.AssOverlay
import okhttp3.OkHttpClient


class MainActivity : AppCompatActivity() {

//    private val url = "http://192.168.0.254:8096/Videos/f5eff7c7-53de-684c-36cd-f4c7cefc99e3/stream?static=true&mediaSourceId=f5eff7c753de684c36cdf4c7cefc99e3&streamOptions=%7B%7D"
    private val url = "http://192.168.0.26:8080/files/c.mkv"

    private lateinit var player:ExoPlayer

    private lateinit var playerView: PlayerView

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.main_track).setOnClickListener {
            selectTrack()
        }
        val okHttpClient = OkHttpClient.Builder()
            .build()

        playerView = findViewById(R.id.main_player)

        val assKeeper = AssKeeper()
        val assSubtitleParserFactory = AssSubtitleParserFactory(assKeeper)
        val mediaFactory = DefaultMediaSourceFactory(
            OkHttpDataSource.Factory(okHttpClient),
            DefaultExtractorsFactory().withAssMkvSupport(assSubtitleParserFactory, assKeeper)
        )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaFactory)
            .build()
        player.addListener(assKeeper)
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(url))

        // TODO move to media library, probably using extension function on ExoPlayer.Builder
        player.setVideoEffects(listOf())
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                // Check if there are any active SSA subtitle tracks
                val hasAss = tracks.groups.any { group ->
                    if (!group.isSelected) return@any false
                    (0 until group.length).any { index ->
                        val track = group.getTrackFormat(index)
                        track.sampleMimeType == MimeTypes.TEXT_SSA || track.codecs == MimeTypes.TEXT_SSA
                    }
                }
                if (hasAss) {
                    player.setVideoEffects(
                        listOf<Effect>(OverlayEffect(listOf(AssOverlay(assKeeper))))
                    )
                } else {
                    player.setVideoEffects(listOf())
                }
            }
        })

        player.prepare()

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    @OptIn(UnstableApi::class)
    private fun selectTrack() {
        TrackSelectionDialogBuilder(this, "aa", player, androidx.media3.common.C.TRACK_TYPE_TEXT).build().show()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

}