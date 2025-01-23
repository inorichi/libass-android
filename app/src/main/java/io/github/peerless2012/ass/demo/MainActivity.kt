package io.github.peerless2012.ass.demo

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import io.github.peerless2012.ass.buildWithAssSupport
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
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        findViewById<Button>(R.id.main_track).setOnClickListener {
            selectTrack()
        }

        player = ExoPlayer.Builder(this)
            .buildWithAssSupport(
                dataSourceFactory = OkHttpDataSource.Factory(OkHttpClient.Builder().build()),
                extractorsFactory = DefaultExtractorsFactory(),
                useEffectsRenderer = true
            )
        playerView = findViewById(R.id.main_player)
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()

    }

    @OptIn(UnstableApi::class)
    private fun selectTrack() {
        TrackSelectionDialogBuilder(this, "aa", player, androidx.media3.common.C.TRACK_TYPE_TEXT)
            .setShowDisableOption(true)
            .build()
            .show()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

}