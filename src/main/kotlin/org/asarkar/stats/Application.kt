package org.asarkar.stats

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.runtime.Micronaut
import java.time.Clock

object Application {
    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.run(Application::class.java)
    }
}

@Factory
class Configuration {
    @Bean
    fun clock(): Clock {
        return Clock.systemUTC()
    }
}