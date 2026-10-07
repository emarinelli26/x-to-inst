package com.emarinelli.xtoinsta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class MainActivity : ComponentActivity() {

    private lateinit var urlInput: EditText
    private lateinit var prepareButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var preview: VideoView
    private lateinit var reelButton: Button
    private lateinit var storyButton: Button

    private var job: Job? = null
    private var exported: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        urlInput = findViewById(R.id.urlInput)
        prepareButton = findViewById(R.id.prepareButton)
        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.statusText)
        preview = findViewById(R.id.preview)
        reelButton = findViewById(R.id.reelButton)
        storyButton = findViewById(R.id.storyButton)

        prepareButton.setOnClickListener { prepare(urlInput.text.toString()) }
        reelButton.setOnClickListener { exported?.let { InstagramSharer.share(this, it, InstagramSharer.Target.REEL) } }
        storyButton.setOnClickListener { exported?.let { InstagramSharer.share(this, it, InstagramSharer.Target.STORY) } }
        preview.setOnPreparedListener { it.isLooping = true }

        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** X's share sheet sends the post link as plain text. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        urlInput.setText(text)
        prepare(text)
    }

    private fun prepare(input: String) {
        val id = TweetFetcher.extractId(input)
        if (id == null) {
            showError(getString(R.string.error_bad_link))
            return
        }
        job?.cancel()
        job = lifecycleScope.launch {
            setBusy(true)
            try {
                val work = File(cacheDir, "work").apply { deleteRecursively(); mkdirs() }

                setStatus(R.string.status_fetching)
                val tweet = withContext(Dispatchers.IO) { TweetFetcher.fetch(id) }

                setStatus(R.string.status_downloading)
                val source = File(work, "source.mp4")
                val avatar = withContext(Dispatchers.IO) {
                    TweetFetcher.download(tweet.videoUrl!!, source)
                    loadAvatar(tweet, work)
                }

                setStatus(R.string.status_composing)
                val (w, h) = withContext(Dispatchers.IO) { VideoComposer.displaySize(source) }
                    .let { if (it.first > 0) it else tweet.videoWidth to tweet.videoHeight }
                val card = withContext(Dispatchers.Default) { CardRenderer.render(tweet, avatar, w, h) }
                val output = File(File(cacheDir, "exports"), "post_${tweet.id}.mp4")
                File(cacheDir, "exports").listFiles()?.forEach { it.delete() }
                VideoComposer.compose(this@MainActivity, source, card, w, h, output)
                source.delete()

                exported = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", output)
                showPreview(output)
                setStatus(R.string.status_ready)
            } catch (e: NoVideoException) {
                showError(getString(R.string.error_no_video))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(getString(R.string.error_generic, e.message ?: e.javaClass.simpleName))
            } finally {
                setBusy(false)
            }
        }
    }

    private fun loadAvatar(tweet: Tweet, work: File): Bitmap? {
        val url = tweet.avatarUrl ?: return null
        return runCatching {
            val file = File(work, "avatar")
            TweetFetcher.download(url, file)
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    private fun showPreview(file: File) {
        preview.visibility = View.VISIBLE
        preview.setVideoURI(Uri.fromFile(file))
        preview.start()
        reelButton.isEnabled = true
        storyButton.isEnabled = true
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        prepareButton.isEnabled = !busy
        if (busy) {
            exported = null
            reelButton.isEnabled = false
            storyButton.isEnabled = false
            preview.stopPlayback()
            preview.visibility = View.INVISIBLE
        }
    }

    private fun setStatus(res: Int) = statusText.setText(res)

    private fun showError(message: String) {
        statusText.text = message
    }
}
