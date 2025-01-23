package io.github.peerless2012.ass.extractor

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import io.github.peerless2012.ass.AssHandler
import io.github.peerless2012.ass.parser.AssSubtitleParserFactory

@OptIn(UnstableApi::class)
fun ExtractorsFactory.withAssMkvSupport(
    assSubtitleParserFactory: AssSubtitleParserFactory,
    assHandler: AssHandler
): ExtractorsFactory {
    return ExtractorsFactory {
        createExtractors()
            .filter { it !is MatroskaExtractor }
            .plus(AssMatroskaExtractor(assSubtitleParserFactory, assHandler))
            .toTypedArray()
    }
}
