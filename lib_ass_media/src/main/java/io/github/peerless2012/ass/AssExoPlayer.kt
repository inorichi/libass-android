package io.github.peerless2012.ass

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import io.github.peerless2012.ass.extractor.withAssMkvSupport
import io.github.peerless2012.ass.parser.AssSubtitleParserFactory

@OptIn(UnstableApi::class)
fun ExoPlayer.Builder.buildWithAssSupport(
    dataSourceFactory: DataSource.Factory,
    extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory(),
    useEffectsRenderer: Boolean = true
): ExoPlayer {
    val assHandler = AssHandler(useEffectsRenderer)
    val assSubtitleParserFactory = AssSubtitleParserFactory(assHandler)

    val mediaSourceFactory = DefaultMediaSourceFactory(
        dataSourceFactory,
        extractorsFactory.withAssMkvSupport(assSubtitleParserFactory, assHandler)
    )

    val player = this
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    assHandler.initPlayer(player)
    return player
}
