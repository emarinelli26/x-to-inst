package com.emarinelli.xtoinsta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TweetFetcherTest {

    @Test
    fun extractsIdFromLinksAndShareText() {
        assertEquals("1585341984679469056", TweetFetcher.extractId("https://x.com/elonmusk/status/1585341984679469056?s=20"))
        assertEquals("123", TweetFetcher.extractId("Mira esto https://twitter.com/user/status/123/video/1"))
        assertEquals("456", TweetFetcher.extractId("https://mobile.twitter.com/a_b/statuses/456"))
        assertNull(TweetFetcher.extractId("https://x.com/user"))
    }

    // Expected values come from running the same expression in Node.js.
    @Test
    fun syndicationTokenMatchesJavaScript() {
        assertEquals("3uchycv2wqc", TweetFetcher.syndicationToken("1585341984679469056"))
        assertEquals("4h2pthqpqcx", TweetFetcher.syndicationToken("1845789012345678901"))
        assertEquals("45fhqezna5p", TweetFetcher.syndicationToken("1712345678901234567"))
    }
}
