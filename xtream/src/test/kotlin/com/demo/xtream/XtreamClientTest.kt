package com.demo.xtream

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.time.Instant
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XtreamClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: XtreamClient

    @BeforeTest fun setUp() {
        server = MockWebServer().also { it.start() }
        client = XtreamClient(Credentials(server.url("/").toString(), "us er", "p@ss"))
    }

    @AfterTest fun tearDown() = server.shutdown()

    private fun reply(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test fun authenticateParsesAccount() = runTest {
        reply("""{"user_info":{"username":"u","status":"Active","auth":1,"exp_date":"1893456000",
            "active_cons":"0","max_connections":"2","allowed_output_formats":["m3u8","ts"]},"server_info":{}}""")
        val info = client.authenticate()
        assertEquals(1893456000L, info.expiresEpochSecs)
        assertEquals(2, info.maxConnections)
        assertEquals(listOf("m3u8", "ts"), info.allowedFormats)
        val req = server.takeRequest()
        assertEquals("/player_api.php", req.requestUrl!!.encodedPath)
        assertEquals("us er", req.requestUrl!!.queryParameter("username"))
        assertEquals("p@ss", req.requestUrl!!.queryParameter("password"))
        assertEquals(Credentials.DEFAULT_USER_AGENT, req.getHeader("User-Agent"))
    }

    @Test fun authenticateRejectsBadLogin() = runTest {
        reply("""{"user_info":{"auth":0}}""")
        assertFailsWith<XtreamException> { client.authenticate() }
    }

    @Test fun httpErrorAndNonJsonBecomeXtreamException() = runTest {
        reply("nope", 403)
        assertFailsWith<XtreamException> { client.authenticate() }
        reply("<html>not json</html>")
        assertFailsWith<XtreamException> { client.authenticate() }
    }

    @Test fun liveStreamsHandleMixedTypes() = runTest {
        reply("""[{"num":"1","name":"BBC One","stream_id":101,"stream_icon":null,"epg_channel_id":"bbc1",
            "category_id":"5","tv_archive":1},{"num":2,"name":"X","stream_id":"102","category_id":7,"tv_archive":0},
            {"name":"no id"}]""")
        val ch = client.liveStreams("5")
        assertEquals(2, ch.size)
        assertEquals("101", ch[0].streamId)
        assertEquals("", ch[0].icon)
        assertTrue(ch[0].hasArchive)
        assertEquals("7", ch[1].categoryId)
        assertEquals("5", server.takeRequest().requestUrl!!.queryParameter("category_id"))
    }

    @Test fun emptyObjectMeansNoItems() = runTest {
        reply("{}")
        assertTrue(client.vodCategories().isEmpty())
    }

    @Test fun authFailureInsideListEndpointIsReported() = runTest {
        reply("""{"user_info":{"auth":0}}""")
        assertFailsWith<XtreamException> { client.liveCategories() }
    }

    @Test fun vodStreamsDefaultExtension() = runTest {
        reply("""[{"stream_id":9,"name":"Film","container_extension":null,"category_id":"1","rating":"7.5"}]""")
        val v = client.vodStreams().single()
        assertEquals("mp4", v.extension)
    }

    @Test fun seriesDetailsGroupsAndSortsSeasons() = runTest {
        reply("""{"info":{"name":"Show","plot":"p","cover":"c.jpg"},"episodes":{
            "2":[{"id":"22","title":"S2E1","episode_num":1,"container_extension":"mkv","info":{"duration_secs":2400}}],
            "1":[{"id":"12","episode_num":2,"container_extension":"mp4"},{"id":"11","episode_num":1,"container_extension":"mp4"}]}}""")
        val d = client.seriesDetails("3")!!
        assertEquals(listOf(1, 2), d.seasons.map { it.number })
        assertEquals(listOf("11", "12"), d.seasons[0].episodes.map { it.id })
        assertEquals("Episode 1", d.seasons[0].episodes[0].title)
        assertEquals("mkv", d.seasons[1].episodes[0].extension)
        assertEquals(2400, d.seasons[1].episodes[0].durationSecs)
    }

    @Test fun seriesWithNoEpisodesIsEmptyNotCrash() = runTest {
        reply("""{"info":{"name":"Show"},"episodes":[]}""")
        assertTrue(client.seriesDetails("3")!!.seasons.isEmpty())
    }

    @Test fun shortEpgDecodesBase64() = runTest {
        val b64 = { s: String -> Base64.getEncoder().encodeToString(s.toByteArray()) }
        reply("""{"epg_listings":[{"title":"${b64("News at Ten")}","description":"${b64("Headlines")}",
            "start_timestamp":"1000","stop_timestamp":"2000"},{"title":"plain text!","start_timestamp":"2000","stop_timestamp":"3000"}]}""")
        val e = client.shortEpg("101")
        assertEquals("News at Ten", e[0].title)
        assertEquals("Headlines", e[0].description)
        assertEquals(2000L, e[0].endEpochSecs)
        assertEquals("plain text!", e[1].title)
    }

    @Test fun vodDetailsNullWhenMissing() = runTest {
        reply("""{"movie_data":{}}""")
        assertNull(client.vodDetails("1"))
    }

    // ---- URL helpers ----

    @Test fun serverRootNormalization() {
        assertEquals("http://x.tv:8080", UrlNormalizer.serverRoot("x.tv:8080"))
        assertEquals("http://x.tv:8080", UrlNormalizer.serverRoot("http://x.tv:8080/c/"))
        assertEquals("https://x.tv", UrlNormalizer.serverRoot(" https://x.tv/player_api.php?username=a&password=b "))
        assertFailsWith<XtreamException> { UrlNormalizer.serverRoot("   ") }
    }

    @Test fun credentialsFromPastedLink() {
        assertEquals("a" to "b", UrlNormalizer.credentialsFromLink("http://x.tv:80/get.php?username=a&password=b&type=m3u"))
        assertNull(UrlNormalizer.credentialsFromLink("http://x.tv"))
    }

    @Test fun streamUrls() {
        val u = StreamUrls(Credentials("http://x.tv:8080/", "us er", "p@ss"))
        assertEquals("http://x.tv:8080/live/us%20er/p@ss/12.m3u8", u.live("12", "m3u8"))
        assertEquals("http://x.tv:8080/movie/us%20er/p@ss/5.mkv", u.movie("5", "mkv"))
        assertEquals("http://x.tv:8080/series/us%20er/p@ss/7.mp4", u.episode("7", ""))
        val ts = u.timeshift("12", 60, Instant.parse("2025-03-04T21:05:00Z"))
        assertTrue(ts.endsWith("/timeshift/us%20er/p@ss/60/2025-03-04:21-05/12.ts"), ts)
    }
}
