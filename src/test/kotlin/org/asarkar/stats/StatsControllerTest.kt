package org.asarkar.stats

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import org.asarkar.stats.StatsController.Companion.STATS_URI
import org.asarkar.stats.StatsController.Companion.TXN_URI
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.annotation.PostConstruct
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

    @Test
    fun testBadBody() {
        try {
            client.toBlocking().exchange(HttpRequest.POST(TXN_URI, "whatever"), Unit::class.java)
            fail<Unit>("Shouldn't be here")
        } catch (ex: HttpClientResponseException) {
            assertThat(ex.status).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun testBadTimestamp() {
        try {
            client.toBlocking().retrieve(HttpRequest.POST(TXN_URI, """{"amount": "1.0", "timestamp": "whatever"}"""))
            fail<Unit>("Shouldn't be here")
        } catch (ex: HttpClientResponseException) {
            assertThat(ex.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    @Test
    fun testBadAmount() {
        try {
            client.toBlocking().retrieve(HttpRequest.POST(TXN_URI, """{"amount": "abc", "timestamp": "2018-07-17T09:59:51.312Z"}"""))
            fail<Unit>("Shouldn't be here")
        } catch (ex: HttpClientResponseException) {
            assertThat(ex.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    @Test
    fun testGoodRequest() {
        val ts = Instant.now().atZone(ZoneOffset.UTC)
                .minusSeconds(1)
                .format(df)
        val response = client.toBlocking()
                .exchange(HttpRequest.POST(TXN_URI, """{"amount": "1.0", "timestamp": "$ts"}"""), Unit::class.java)
        assertThat(response.status).isEqualTo(HttpStatus.CREATED)
    }

    @Test
    fun testStaleRequest() {
        val ts = Instant.now().atZone(ZoneOffset.UTC)
                .minusHours(1L)
                .format(df)
        val response = client.toBlocking()
                .exchange(HttpRequest.POST(TXN_URI, """{"amount": "1.0", "timestamp": "$ts"}"""), Unit::class.java)
        assertThat(response.status).isEqualTo(HttpStatus.NO_CONTENT)
    }

    @Test
    fun testFutureRequest() {
        try {
            client.toBlocking().retrieve(HttpRequest.POST(TXN_URI, """{"amount": "abc", "timestamp": "2099-07-17T09:59:51.312Z"}"""))
            fail<Unit>("Shouldn't be here")
        } catch (ex: HttpClientResponseException) {
            assertThat(ex.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    @Test
    fun testDelete() {
        val response = client.toBlocking()
                .exchange(HttpRequest.DELETE<Unit>(TXN_URI), Unit::class.java)
        assertThat(response.status).isEqualTo(HttpStatus.NO_CONTENT)
        val bucket = client.toBlocking()
                .retrieve(STATS_URI, Bucket::class.java)
        val zero = BigDecimal("0.00")
        assertThat(bucket.count).isEqualTo(0)
        assertThat(bucket.sum).isEqualTo(zero)
        assertThat(bucket.max).isEqualTo(zero)
        assertThat(bucket.min).isEqualTo(zero)
        assertThat(bucket.avg).isEqualTo(zero)
    }

    @Test
    fun testGetStats() {
        val ts = Instant.now().atZone(ZoneOffset.UTC)
                .minusSeconds(1)
                .format(df)
        val response = client.toBlocking()
                .exchange(HttpRequest.POST(TXN_URI, """{"amount": "1.0", "timestamp": "$ts"}"""), Unit::class.java)
        assertThat(response.status).isEqualTo(HttpStatus.CREATED)

        val bucket = client.toBlocking()
                .retrieve(STATS_URI, Bucket::class.java)
        val one = BigDecimal("1.00")
        assertThat(bucket.count).isEqualTo(1)
        assertThat(bucket.sum).isEqualTo(one)
        assertThat(bucket.max).isEqualTo(one)
        assertThat(bucket.min).isEqualTo(one)
        assertThat(bucket.avg).isEqualTo(one)
    }
}