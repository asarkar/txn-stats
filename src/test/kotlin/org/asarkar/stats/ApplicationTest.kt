package org.asarkar.stats

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject

@MicronautTest(application = Application::class)
class ApplicationTest {
    private val logger = LoggerFactory.getLogger(ApplicationTest::class.java)

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

    @BeforeEach
    fun beforeEach() {
        val deleted = client.toBlocking()
                .exchange(HttpRequest.DELETE<Unit>(StatsController.TXN_URI), Unit::class.java)
        assertThat(deleted.status).isEqualTo(HttpStatus.NO_CONTENT)
    }

    @ParameterizedTest(name = "http0{0}.json")
    @ValueSource(ints = [0, 1, 2, 3, 4, 5])
    fun testApp(i: Int) {
        val filename = "http0$i.json"
        logger.info("Executing tests from file: {}", filename)
        javaClass.getResourceAsStream("/testcases/$filename")
                .bufferedReader()
                .useLines {
                    it.forEach { line ->
                        val e = TestFileParser.parseLine(line)
                        if (e.pair != null) {
                            val request = e.pair.first
                            val expectedResponse = e.pair.second
                            try {
                                val response: HttpResponse<out Any> = if (expectedResponse.body() != null) {
                                    client
                                            .toBlocking()
                                            .exchange(request, Bucket::class.java)
                                } else {
                                    client
                                            .toBlocking()
                                            .exchange(request)
                                }
                                assertThat(response.status())
                                        .withFailMessage("Status code: %s is not equal to that in line: %s", response.status(), line)
                                        .isEqualTo(expectedResponse.status)
                                expectedResponse.headers
                                        .forEach { k, v ->
                                            assertThat(response.headers[k])
                                                    .withFailMessage("Header: %s is not equal to that in line: %s", response.headers[k], line)
                                                    .contains(v)
                                        }
                                assertThat(response.body())
                                        .withFailMessage("Response body: %s is not equal to that in line: %s", response.body(), line)
                                        .isEqualTo(expectedResponse.body())
                            } catch (ex: HttpClientResponseException) {
                                assertThat(ex.status)
                                        .withFailMessage("Status code: %s is not equal to that in line: %s", ex.status, line)
                                        .isEqualTo(expectedResponse.status)
                                expectedResponse.headers
                                        .forEach { k, v ->
                                            assertThat(ex.response.headers[k])
                                                    .withFailMessage("Header: %s is not equal to that in line: %s", ex.response.headers[k], line)
                                                    .contains(v)
                                        }
                            }
                        } else {
                            logger.debug("Going to sleep for: {} ms", e.sleep!!)
                            TimeUnit.MILLISECONDS.sleep(e.sleep)
                        }
                    }
                }
    }
}