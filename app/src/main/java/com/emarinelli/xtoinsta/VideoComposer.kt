package com.emarinelli.xtoinsta

import android.content.Context
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Places the source video inside the card's hole and exports a 1080x1920 MP4. */
@UnstableApi
object VideoComposer {

    /** Width and height as the video is displayed, taking rotation metadata into account. */
    fun displaySize(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            return if (rotation % 180 != 0) h to w else w to h
        } finally {
            retriever.release()
        }
    }

    /** Must be called from the main thread: Transformer reports back on the thread that started it. */
    suspend fun compose(context: Context, input: File, card: RenderedCard, videoW: Int, videoH: Int, output: File) {
        output.parentFile?.mkdirs()
        output.delete()

        val effects = ImmutableList.of<Effect>(
            Presentation.createForWidthAndHeight(CardRenderer.WIDTH, CardRenderer.HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT),
            placementMatrix(card, videoW, videoH).let { m -> MatrixTransformation { m } },
            OverlayEffect(ImmutableList.of(BitmapOverlay.createStaticBitmapOverlay(card.bitmap))),
        )
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input)))
            .setEffects(Effects(ImmutableList.of(), effects))
            .build()

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        cont.resume(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        cont.resumeWithException(exportException)
                    }
                })
                .build()
            cont.invokeOnCancellation { transformer.cancel() }
            transformer.start(item, output.absolutePath)
        }
    }

    /**
     * After Presentation the video is letterboxed in the middle of the 9:16 frame. This matrix,
     * in normalized device coordinates (-1..1, y pointing up), moves and scales it onto the card's hole.
     */
    private fun placementMatrix(card: RenderedCard, videoW: Int, videoH: Int): Matrix {
        val frameW = CardRenderer.WIDTH.toFloat()
        val frameH = CardRenderer.HEIGHT.toFloat()
        val aspect = if (videoW > 0 && videoH > 0) videoW.toFloat() / videoH else card.videoRect.width() / card.videoRect.height()

        // Half-width of the letterboxed video in NDC.
        val halfW = if (aspect >= frameW / frameH) 1f else frameH * aspect / frameW

        val rect = card.videoRect
        val targetHalfW = rect.width() / frameW
        val centerX = rect.centerX() / frameW * 2f - 1f
        val centerY = 1f - rect.centerY() / frameH * 2f
        val scale = targetHalfW / halfW

        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(centerX, centerY)
        }
    }
}
