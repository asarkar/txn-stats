package org.asarkar.stats

import com.fasterxml.jackson.core.JsonParseException
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import org.slf4j.LoggerFactory

@Controller
class StatsController(private val stats: Statistics) {
    private val logger = LoggerFactory.getLogger(StatsController::class.java)

    @Get(value = STATS_URI, produces = [MediaType.APPLICATION_JSON])
    fun getStats(): Bucket {
        return stats.aggregate()
    }

    @Post(TXN_URI, consumes = [MediaType.APPLICATION_JSON])
    fun updateStats(@Body txn: Transaction): HttpResponse<Unit> {
        return if (stats.update(txn)) HttpResponse.status(HttpStatus.CREATED)
        else {
            logger.warn("Skipped stale transaction: {}", txn)
            HttpResponse.noContent()
        }
    }

    @Delete(TXN_URI)
    fun resetStats(): HttpResponse<Unit> {
        stats.reset()
        logger.warn("Reset stats")
        return HttpResponse.noContent()
    }

    @Error(exception = IllegalArgumentException::class)
    fun illegalArgumentException(ex: IllegalArgumentException): HttpResponse<Unit> {
        logger.error(ex.message, ex)
        return HttpResponse.unprocessableEntity()
    }

    @Error(exception = JsonParseException::class)
    fun jsonParseException(ex: JsonParseException): HttpResponse<Unit> {
        logger.error(ex.message, ex)
        return HttpResponse.badRequest()
    }

    @Error(exception = ConversionErrorException::class)
    fun conversionErrorException(ex: ConversionErrorException): HttpResponse<Unit> {
        logger.error(ex.message, ex)
        return HttpResponse.unprocessableEntity()
    }

    @Error(exception = Exception::class)
    fun otherException(ex: Exception): HttpResponse<Unit> {
        logger.error(ex.message, ex)
        return HttpResponse.serverError()
    }

    companion object {
        const val STATS_URI = "/statistics"
        const val TXN_URI = "/transactions"
    }
}