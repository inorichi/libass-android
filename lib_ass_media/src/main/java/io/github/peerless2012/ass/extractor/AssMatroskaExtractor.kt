package io.github.peerless2012.ass.extractor

import android.util.SparseArray
import androidx.annotation.OptIn
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.mkv.EbmlProcessor
import androidx.media3.extractor.mkv.MatroskaExtractor
import io.github.peerless2012.ass.AssKeeper
import io.github.peerless2012.ass.factory.AssSubtitleParserFactory

@OptIn(UnstableApi::class)
class AssMatroskaExtractor(
    subtitleParserFactory: AssSubtitleParserFactory,
    private val assKeeper: AssKeeper
) : MatroskaExtractor(subtitleParserFactory) {

    private var currentAttachmentName: String? = null
    private var currentAttachmentMime: String? = null

    override fun getElementType(id: Int): Int {
        return when (id) {
            ID_ATTACHMENTS -> EbmlProcessor.ELEMENT_TYPE_MASTER
            ID_ATTACHED_FILE -> EbmlProcessor.ELEMENT_TYPE_MASTER
            ID_FILE_NAME -> EbmlProcessor.ELEMENT_TYPE_STRING
            ID_FILE_MIME_TYPE -> EbmlProcessor.ELEMENT_TYPE_STRING
            ID_FILE_DATA -> EbmlProcessor.ELEMENT_TYPE_BINARY
            else -> super.getElementType(id)
        }
    }

    override fun isLevel1Element(id: Int): Boolean {
        return super.isLevel1Element(id) || id == ID_ATTACHMENTS
    }

    override fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long) {
        when (id) {
            ID_ATTACHED_FILE -> clearAttachment()
            else -> super.startMasterElement(id, contentPosition, contentSize)
        }
    }

    override fun endMasterElement(id: Int) {
        when (id) {
            ID_ATTACHED_FILE -> clearAttachment()
            ID_BLOCK_GROUP -> {
                readBlockGroup()
                super.endMasterElement(id)
            }
            else -> super.endMasterElement(id)
        }
    }

    override fun stringElement(id: Int, value: String) {
        when (id) {
            ID_FILE_NAME -> currentAttachmentName = value
            ID_FILE_MIME_TYPE -> currentAttachmentMime = value
            else -> super.stringElement(id, value)
        }
    }

    override fun binaryElement(id: Int, contentSize: Int, input: ExtractorInput) {
        when (id) {
            ID_FILE_DATA -> {
                val attachmentName = requireNotNull(currentAttachmentName)
                val attachmentMime = requireNotNull(currentAttachmentMime)
                if (attachmentMime in fontMimeTypes) {
                    val data = ByteArray(contentSize)
                    input.readFully(data, 0, contentSize)
                    assKeeper.ass.addFont(attachmentName, data)
                } else {
                    input.skipFully(contentSize)
                }
            }
            else -> super.binaryElement(id, contentSize, input)
        }
    }

    private fun clearAttachment() {
        currentAttachmentName = null
        currentAttachmentMime = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun readBlockGroup() {
        val tracks = tracks.get(this) as SparseArray<Track>
        val track = tracks.get(blockTrackNumber.get(this) as Int)
        if (track.codecId == CODEC_ID_ASS) {
            for (i in 0 until blockSampleCount.get(this) as Int) {
                val start = (blockTimeUs.get(this) as Long) + (i * track.defaultSampleDurationNs) / 1000
                val end = start + blockDurationUs.get(this) as Long
                val event = (subtitleSample.get(this) as ParsableByteArray).run {
                    data.decodeToString(position + SSA_PREFIX, limit())
                }
                val dialogue = "Dialogue: %s,%s,%s".format(start.toAssTime(), end.toAssTime(), event)

                synchronized("") {
                    assKeeper.track.readBuffer(dialogue.encodeToByteArray())
                }
            }
        }
    }

    private fun Long.toAssTime(): String {
        val total = this / 10_000
        val hours = total / (60 * 60 * 100)
        val minutes = (total / (60 * 100)) % 60
        val seconds = (total / 100) % 60
        val centiseconds = total % 100
        return "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centiseconds)
    }

    companion object {
        const val ID_ATTACHMENTS = 0x1941A469
        const val ID_ATTACHED_FILE = 0x61A7
        const val ID_FILE_NAME = 0x466E
        const val ID_FILE_MIME_TYPE = 0x4660
        const val ID_FILE_DATA = 0x465C
        const val ID_BLOCK_GROUP = 0xA0

        const val CODEC_ID_ASS: String = "S_TEXT/ASS"

        val fontMimeTypes = listOf(
            "font/ttf",
            "font/otf",
            "font/sfnt",
            "font/woff",
            "font/woff2",
            "application/font-sfnt",
            "application/font-woff",
            "application/x-truetype-font",
            "application/vnd.ms-opentype",
            "application/x-font-ttf",
        )

        val blockTimeUs = MatroskaExtractor::class.java.getDeclaredField("blockTimeUs").apply {
            isAccessible = true
        }
        val blockDurationUs = MatroskaExtractor::class.java.getDeclaredField("blockDurationUs").apply {
            isAccessible = true
        }
        val subtitleSample = MatroskaExtractor::class.java.getDeclaredField("subtitleSample").apply {
            isAccessible = true
        }
        val tracks = MatroskaExtractor::class.java.getDeclaredField("tracks").apply {
            isAccessible = true
        }
        val blockTrackNumber = MatroskaExtractor::class.java.getDeclaredField("blockTrackNumber").apply {
            isAccessible = true
        }
        val blockSampleCount = MatroskaExtractor::class.java.getDeclaredField("blockSampleCount").apply {
            isAccessible = true
        }
        val SSA_PREFIX = MatroskaExtractor::class.java.getDeclaredField("SSA_PREFIX").apply {
            isAccessible = true
        }.get(null).let { (it as ByteArray).size }
    }
}
