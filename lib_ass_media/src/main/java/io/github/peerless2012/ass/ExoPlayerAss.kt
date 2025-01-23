package io.github.peerless2012.ass

import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.effect.OverlayEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import io.github.peerless2012.ass.extractor.withAssMkvSupport
import io.github.peerless2012.ass.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.render.AssOverlay

@OptIn(UnstableApi::class)
fun ExoPlayer.Builder.buildWithAssSupport(
    dataSourceFactory: DataSource.Factory,
    extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory(),
    useEffectsRenderer: Boolean = true
): ExoPlayer {
    val assKeeper = AssKeeper(useEffectsRenderer)
    val assSubtitleParserFactory = AssSubtitleParserFactory(assKeeper)

    val mediaSourceFactory = DefaultMediaSourceFactory(
        dataSourceFactory,
        extractorsFactory.withAssMkvSupport(assSubtitleParserFactory, assKeeper)
    )

    val player = this
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    player.addListener(assKeeper)
    if (useEffectsRenderer) {
        // Video effects need to be called before prepare at least once
        player.setVideoEffects(listOf())

        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                // Check if there are any active SSA subtitle tracks
                val hasAss = tracks.groups.any { group ->
                    if (group.isSelected) {
                        (0 until group.length).any { index ->
                            val track = group.getTrackFormat(index)
                            track.sampleMimeType == MimeTypes.TEXT_SSA || track.codecs == MimeTypes.TEXT_SSA
                        }
                    } else {
                        false
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
    }
    return player
}
