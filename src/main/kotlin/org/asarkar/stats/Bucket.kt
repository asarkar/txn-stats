package org.asarkar.stats

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.util.Objects

data class Transaction(val amount: BigDecimal, val timestamp: Instant) {
    fun durationMillis(start: Instant): Long {
        return Duration.between(start, timestamp).abs().toMillis()
    }
}

class BigDecimalSerializer : StdSerializer<BigDecimal>(BigDecimal::class.java) {
    override fun serialize(value: BigDecimal, gen: JsonGenerator, provider: SerializerProvider?) {
        gen.writeString(decimalFormat.format(value.setScale(2, RoundingMode.HALF_UP)))
    }

    companion object {
        private val decimalFormat = DecimalFormat("0.00")
    }
}

class Bucket(
        @JsonSerialize(using = BigDecimalSerializer::class)
        val sum: BigDecimal = BigDecimal.ZERO,
        @JsonSerialize(using = BigDecimalSerializer::class)
        val max: BigDecimal = BigDecimal.ZERO,
        @JsonSerialize(using = BigDecimalSerializer::class)
        val min: BigDecimal = BigDecimal.ZERO,
        val count: Long = 0L,
        @JsonIgnore
        val startTime: Instant = Instant.EPOCH
) {
    @JsonSerialize(using = BigDecimalSerializer::class)
    val avg: BigDecimal = (if (count > 0) sum.divide(BigDecimal.valueOf(count), RoundingMode.HALF_UP)
    else BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)

    @JsonIgnore
    fun reduce(other: Bucket): Bucket {
        return Bucket(
                sum = sum.add(other.sum),
                max = maxOf(max, other.max),
                min = minOf(min, other.min),
                count = count + other.count,
                startTime = if (startTime.isBefore(other.startTime)) startTime else other.startTime
        )
    }

    companion object {
        fun update(prev: Bucket?, txn: Transaction, newStartTime: Instant): Bucket {
            return if (prev != null) {
                Bucket(
                        sum = prev.sum.add(txn.amount)
                                .setScale(2, RoundingMode.HALF_UP),
                        max = maxOf(prev.max, txn.amount),
                        min = minOf(prev.min, txn.amount),
                        count = prev.count + 1,
                        startTime = prev.startTime
                )
            } else {
                val amt = txn.amount.setScale(2, RoundingMode.HALF_UP)
                Bucket(
                        sum = amt,
                        max = amt,
                        min = amt,
                        count = 1,
                        startTime = newStartTime
                )
            }
        }

        private fun maxOf(x: BigDecimal, y: BigDecimal): BigDecimal {
            val max = listOf(x, y)
                    .filter { it > BigDecimal.ZERO }
                    .max() ?: BigDecimal.ZERO
            return max.setScale(2, RoundingMode.HALF_UP)
        }

        private fun minOf(x: BigDecimal, y: BigDecimal): BigDecimal {
            val min = listOf(x, y)
                    .filter { it > BigDecimal.ZERO }
                    .min() ?: BigDecimal.ZERO
            return min.setScale(2, RoundingMode.HALF_UP)
        }
    }

    @JsonIgnore
    fun durationMillis(start: Instant) = Duration.between(startTime, start).abs().toMillis()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as Bucket

        return sum == other.sum &&
                max == other.max &&
                min == other.min &&
                count == other.count
    }

    override fun hashCode(): Int {
        return Objects.hash(sum, max, min, count)
    }

    override fun toString(): String {
        return "Bucket(sum=$sum, max=$max, min=$min, count=$count, avg=$avg, startTime=$startTime)"
    }
}