package org.asarkar.stats

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant

class StatisticsTest {
    @Test
    fun testUpdateAndAggregate() {
        val mockClock = Mockito.mock(Clock::class.java)
        // 60 buckets of size 1 sec each
        val statistics = Statistics(1000, 60000, mockClock)

        // current time is epoch + 60 sec
        Mockito.`when`(mockClock.instant())
                .thenReturn(Instant.EPOCH.plusSeconds(60L))
        // submit 60 txn parallelly with start times one second apart
        runBlocking {
            val updated = (0..59)
                    .map { Transaction(BigDecimal.valueOf(it.toLong()), Instant.EPOCH.plusSeconds(it.toLong())) }
                    .pmap { statistics.update(it) }
                    .all { it }
            assertThat(updated).isTrue()
        }

        // current time is epoch + 90 sec; buckets with start times before (epoch + 30 sec) are outdated
        Mockito.`when`(mockClock.instant())
                .thenReturn(Instant.EPOCH.plusSeconds(90L))
        val expected = Bucket(
                sum = BigDecimal.valueOf((30..59).sum().toLong())
                        .setScale(2, RoundingMode.HALF_UP),
                max = BigDecimal.valueOf(59L)
                        .setScale(2, RoundingMode.HALF_UP),
                min = BigDecimal.valueOf(30L)
                        .setScale(2, RoundingMode.HALF_UP),
                count = 30L
        )
        assertThat(statistics.aggregate()).isEqualTo(expected)
    }

    private suspend fun <A, B> Iterable<A>.pmap(f: suspend (A) -> B): List<B> = coroutineScope {
        map { async { f(it) } }.map { it.await() }
    }
}