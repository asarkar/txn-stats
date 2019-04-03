package org.asarkar.stats

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class Either(val pair: Pair<MutableHttpRequest<String?>, MutableHttpResponse<Bucket?>>?, val sleep: Long?)

object TestFileParser {
    private val objectMapper = jacksonObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    private val df = DateTimeFormatter.ofPattern("YYYY-MM-dd'T'HH:mm:ss.SSS'Z'")

    fun parseLine(line: String): Either {
        val tree = objectMapper.readTree(line)
        if (tree.path("sleep").canConvertToLong()) {
            return Either(null, tree.path("sleep").longValue())
        }

        val request = tree.path("request").also {
            require(!it.isMissingNode) { "Missing request in line: $line" }
        }

        val method = request.path("method").let {
            try {
                HttpMethod.valueOf(it.textValue())
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("Missing or invalid request method: ${it.textValue()} in line: $line")
            }
        }

        val uri = request.path("url").let {
            require(!it.isMissingNode) { "Missing request URI in line: $line" }
            it.textValue()
        }

        val body = request.path("body")
        val httpRequest = if (method == HttpMethod.POST && !body.isMissingNode) {
            val timestamp = body
                    .path("_timestampOffset")
                    .takeIf { it.canConvertToLong() }
                    ?.longValue()
                    ?.let {
                        Instant.now().atZone(ZoneOffset.UTC)
                                .plus(it, ChronoUnit.MILLIS)
                                .format(df)
                    }
            val amount = body
                    .path("amount")
                    .textValue()

            if (timestamp == null && amount == null) {
                HttpRequest.POST(uri, body.textValue())
            } else {
                HttpRequest.POST(uri, objectMapper.writeValueAsString(mapOf("amount" to amount, "timestamp" to timestamp)))
            }
        } else {
            HttpRequest.create(method, uri)
        }

        if (request.has("headers")) {
            objectMapper.convertValue(request.path("headers"), Map::class.java)
                    .forEach { (k, v) -> httpRequest.header(k.toString(), v.toString()) }
        }

        val response = tree.path("response").also {
            require(!it.isMissingNode) { "Missing response in line: $line" }
        }
        val status = response.path("status_code").let {
            try {
                HttpStatus.valueOf(it.intValue())
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("Missing or invalid response status code: ${it.textValue()} in line: $line")
            }
        }
        val httpResponse: MutableHttpResponse<Bucket?> = HttpResponse.status<Bucket>(status)

        if (method == HttpMethod.GET) {
            try {
                httpResponse.body(objectMapper.convertValue(response.path("body"), Bucket::class.java))
            } catch (ex: RuntimeException) {
                throw IllegalArgumentException("Missing or invalid response body in line: $line")
            }
        }
        if (response.has("headers")) {
            objectMapper.convertValue(response.path("headers"), Map::class.java)
                    .forEach { (k, v) -> httpResponse.header(k.toString(), v.toString()) }
        }

        return Either(httpRequest to httpResponse, null)
    }
}