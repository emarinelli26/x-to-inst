package com.emarinelli.xtoinsta

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Tweet(
    val id: String,
    val authorName: String,
    val authorHandle: String,
    val avatarUrl: String?,
    val text: String,
    val videoUrl: String?,
    val videoWidth: Int,
    val videoHeight: Int,
) {
    val permalink: String get() = "x.com/$authorHandle/status/$id"
}

class NoVideoException : Exception("Post without video")

/**
 * Reads a public post from X without the paid official API.
 *
 * Tries FxTwitter's public API first and falls back to the syndication endpoint that
 * powers X's own embedded tweets. Both are unofficial and can change without notice.
 */
object TweetFetcher {

    private val STATUS_ID = Regex("""(?:twitter\.com|x\.com)/[^/\s]+/status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE)
    private val TRAILING_TCO = Regex("""\s*https://t\.co/\w+\s*$""")
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) XToInsta/0.1"

    /** Returns the post id from a link or from the text X puts in its share sheet. */
    fun extractId(text: String): String? = STATUS_ID.find(text)?.groupValues?.get(1)

    fun fetch(id: String): Tweet {
        val errors = mutableListOf<String>()
        for (source in listOf(::fetchFx, ::fetchSyndication)) {
            try {
                return source(id)
            } catch (e: NoVideoException) {
                throw e
            } catch (e: Exception) {
                errors += "${e.javaClass.simpleName}: ${e.message}"
            }
        }
        throw IOException("No pude leer el post ($errors)")
    }

    private fun fetchFx(id: String): Tweet {
        val tweet = JSONObject(httpGet("https://api.fxtwitter.com/status/$id")).getJSONObject("tweet")
        val author = tweet.getJSONObject("author")
        val video = tweet.optJSONObject("media")?.optJSONArray("videos")?.optJSONObject(0)
            ?: throw NoVideoException()
        return Tweet(
            id = id,
            authorName = author.optString("name"),
            authorHandle = author.optString("screen_name"),
            avatarUrl = author.optString("avatar_url").ifBlank { null },
            text = cleanText(tweet.optString("text")),
            videoUrl = video.getString("url"),
            videoWidth = video.optInt("width"),
            videoHeight = video.optInt("height"),
        )
    }

    private fun fetchSyndication(id: String): Tweet {
        val url = "https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=es&token=${syndicationToken(id)}"
        val json = JSONObject(httpGet(url))
        val user = json.getJSONObject("user")
        val media = json.optJSONArray("mediaDetails")
        var videoInfo: JSONObject? = null
        for (i in 0 until (media?.length() ?: 0)) {
            val item = media!!.getJSONObject(i)
            if (item.optString("type") in setOf("video", "animated_gif")) {
                videoInfo = item.optJSONObject("video_info")
                break
            }
        }
        if (videoInfo == null) throw NoVideoException()

        val variants = videoInfo.getJSONArray("variants")
        var best: JSONObject? = null
        for (i in 0 until variants.length()) {
            val v = variants.getJSONObject(i)
            if (v.optString("content_type") != "video/mp4") continue
            if (best == null || v.optInt("bitrate") > best.optInt("bitrate")) best = v
        }
        val aspect = videoInfo.optJSONArray("aspect_ratio")
        return Tweet(
            id = id,
            authorName = user.optString("name"),
            authorHandle = user.optString("screen_name"),
            avatarUrl = user.optString("profile_image_url_https").replace("_normal.", "_400x400.").ifBlank { null },
            text = cleanText(json.optString("text")),
            videoUrl = best?.getString("url") ?: throw NoVideoException(),
            videoWidth = aspect?.optInt(0) ?: 0,
            videoHeight = aspect?.optInt(1) ?: 0,
        )
    }

    private fun cleanText(raw: String): String = raw
        .replace(TRAILING_TCO, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    /** Same token X's embed widget computes: ((id / 1e15) * PI).toString(36) without zeros or dots. */
    internal fun syndicationToken(id: String): String =
        jsToString36(id.toDouble() / 1e15 * Math.PI).replace(Regex("(0+|\\.)"), "")

    /** Port of V8's DoubleToRadixCString for radix 36, so the token matches the browser's. */
    internal fun jsToString36(value: Double): String {
        val radix = 36
        val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
        var integer = Math.floor(value)
        var fraction = value - integer
        var delta = maxOf(0.5 * (Math.nextUp(value) - value), Math.nextUp(0.0))
        val frac = StringBuilder()
        if (fraction >= delta) {
            do {
                fraction *= radix
                delta *= radix
                val digit = fraction.toInt()
                frac.append(chars[digit])
                fraction -= digit
                if (fraction > 0.5 || (fraction == 0.5 && (digit and 1) == 1)) {
                    if (fraction + delta > 1) {
                        // Round up, propagating carries into the integer part if needed.
                        while (true) {
                            if (frac.isEmpty()) {
                                integer += 1
                                break
                            }
                            val d = chars.indexOf(frac[frac.length - 1])
                            frac.setLength(frac.length - 1)
                            if (d + 1 < radix) {
                                frac.append(chars[d + 1])
                                break
                            }
                        }
                        break
                    }
                }
            } while (fraction >= delta)
        }
        val intPart = java.math.BigDecimal(integer).toBigInteger().toString(radix)
        return if (frac.isEmpty()) intPart else "$intPart.$frac"
    }

    fun httpGet(url: String): String {
        val conn = open(url)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, target: File) {
        val conn = open(url)
        try {
            target.parentFile?.mkdirs()
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code en $url")
        }
        return conn
    }
}
