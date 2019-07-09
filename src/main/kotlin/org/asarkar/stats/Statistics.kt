package org.asarkar.stats

import io.micronaut.context.annotation.Value
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReferenceArray
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
class Statistics(
        @Value("\${stats.bucket-size-millis:100}")
        private val bucketSizeMillis: Int = 100,
        @Value("\${stats.duration-millis:60000}")
        private val durationMillis: Long = 60000L,
        private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(Statistics::class.java)
    private var buckets = newBuckets()

    private fun newBuckets(): AtomicReferenceArray<Bucket> {
        val count = ceil(durationMillis.toDouble() / bucketSizeMillis).toInt()
        logger.debug("Initializing buckets, size: {} ms, count: {}, duration: {} ms", bucketSizeMillis, count, durationMillis)
        return AtomicReferenceArray(count)
    }

    fun update(txn: Transaction): Boolean {
        val now = Instant.now(clock)
        if (now.isBefore(txn.timestamp)) {
            throw IllegalArgumentException("Current time: $now; ${txn.timestamp} is in the future")
        }
        val diff = txn.durationMillis(now)
        logger.debug("Current time: {}, txn timestamp: {}, difference: {}", now, txn.timestamp, diff)

        return if (diff > durationMillis) {
            logger.info("Ignored stale txn: {}", txn)
            false
        } else {
            val offset = txn.durationMillis(Instant.EPOCH) / bucketSizeMillis
            val index = (offset % buckets.length()).toInt()
            val startTime = Instant.EPOCH.plusMillis(offset * bucketSizeMillis)
            buckets.updateAndGet(index) { b ->
                Bucket.update(b?.takeUnless { it.durationMillis(now) > durationMillis }, txn, startTime)
            }
            logger.debug("Added txn: {} to bucket[{}]: {}", txn, index, buckets[index])
            true
        }
    }

    fun aggregate(): Bucket {
        val now = Instant.now(clock)
        return (0 until buckets.length())
                // Further reading: http://gee.cs.oswego.edu/dl/html/j9mm.html
                .map(buckets::get)
                .filter {
                    val take = it != null && it.durationMillis(now) <= durationMillis
                    if (it != null && !take) {
                        logger.debug("Current time: {}; bucket is outdated: {}", now, it)
                    }
                    take
                }
                .fold(Bucket(startTime = now.minus(durationMillis, ChronoUnit.MILLIS))) { acc, b -> acc.reduce(b) }
    }

    @Synchronized
    fun reset() {
        buckets = newBuckets()
    }
}