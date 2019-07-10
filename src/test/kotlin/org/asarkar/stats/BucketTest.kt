package org.asarkar.stats

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BucketTest {
    private val objectMapper = jacksonObjectMapper()
    @Test
    fun testSerialize() {
        val bucket = Bucket(startTime = Instant.now(), count = 1L)
        val str = objectMapper.writeValueAsString(bucket)
        val tree = objectMapper.readTree(str)
        val zero = "0.00"
        assertThat(tree.path("sum").textValue()).isEqualTo(zero)
        assertThat(tree.path("max").textValue()).isEqualTo(zero)
        assertThat(tree.path("min").textValue()).isEqualTo(zero)
        assertThat(tree.path("avg").textValue()).isEqualTo(zero)
    }

    @Test
    fun testDeserialize() {
        val bucket = objectMapper.readValue(
                """{"sum":"1.00","max":"1.00","min":"1.00","count":1,"avg":"1.00"}""",
                Bucket::class.java
        )
        assertThat(bucket.count).isEqualTo(1)
        val one = BigDecimal("1.00")
        assertThat(bucket.sum).isEqualTo(one)
        assertThat(bucket.max).isEqualTo(one)
        assertThat(bucket.min).isEqualTo(one)
        assertThat(bucket.avg).isEqualTo(one)
        assertThat(bucket.startTime).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun testUpdate() {
        val bucket = Bucket(startTime = Instant.now(), count = 0L)
        val txn = Transaction(BigDecimal.TEN, Instant.EPOCH)
        val updated = Bucket.update(bucket, txn, txn.timestamp)
        assertThat(bucket).isNotSameAs(updated)
        assertThat(updated.count).isEqualTo(1)
        val ten = BigDecimal("10.00")
        assertThat(updated.sum).isEqualTo(ten)
        assertThat(updated.max).isEqualTo(ten)
        assertThat(updated.min).isEqualTo(ten)
        assertThat(updated.avg).isEqualTo(ten)
        assertThat(updated.startTime).isEqualTo(bucket.startTime)
    }

    @Test
    fun testReduce() {
        val bucket1 = Bucket(
                sum = BigDecimal.ONE,
                max = BigDecimal.ONE,
                min = BigDecimal.ONE,
                count = 1,
                startTime = Instant.now()
        )
        val bucket2 = Bucket(
                sum = BigDecimal.TEN,
                max = BigDecimal.TEN,
                min = BigDecimal.TEN,
                count = 1,
                startTime = Instant.EPOCH
        )

        val reduced = bucket1.reduce(bucket2)
        assertThat(reduced.count).isEqualTo(2)
        assertThat(reduced.sum).isEqualTo(BigDecimal.valueOf(11))
        assertThat(reduced.max).isEqualTo("10.00")
        assertThat(reduced.min).isEqualTo("1.00")
        assertThat(reduced.avg).isEqualTo("6.00")
        assertThat(reduced.startTime).isEqualTo(bucket2.startTime)
    }

    @Test
    fun testDurationMillis() {
        val bucket = Bucket(startTime = Instant.now())
        assertThat(bucket.durationMillis(bucket.startTime.plusSeconds(2L))).isEqualTo(2000L)
    }
}