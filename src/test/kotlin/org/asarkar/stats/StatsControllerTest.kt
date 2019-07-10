package org.asarkar.stats

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import org.asarkar.stats.StatsController.Companion.TXN_URI
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject

@MicronautTest(application = Application::class)
class StatsControllerTest {
    private val df = DateTimeFormatter.ofPattern("YYYY-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Inject
    private lateinit var server: EmbeddedServer
    private lateinit var client: RxHttpClient

    @PostConstruct
    fun postConstruct() {
        client = RxHttpClient.create(server.url)
    }

    @PreDestroy
    fun preDestroy() {
        client.close()
    }

    @Test
    fun testBadBody() {
        client.exchange(HttpRequest.POST(TXN_URI, "whatever"), Unit::class.java)
            .test()
            .assertError { it is HttpClientResponseException && it.status == HttpStatus.BAD_REQUEST }
            .assertNotComplete()
            .awaitTerminalEvent(1, TimeUnit.SECONDS)
    }

    @Test
    fun testGoodRequest() {
        val ts = Instant.now().atZone(ZoneOffset.UTC)
            .minusSeconds(1)
            .format(df)
        client.exchange(HttpRequest.POST(TXN_URI, """{"amount": "1.0", "timestamp": "$ts"}"""), Unit::class.java)
            .test()
            .assertValue { it.status == HttpStatus.CREATED }
            .assertComplete()
            .awaitTerminalEvent(1, TimeUnit.SECONDS)
    }
}