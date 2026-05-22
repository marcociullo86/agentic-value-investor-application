package com.valueinvesting.webapp.config

import com.valueinvesting.webapp.fmp.FmpResilienceConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class FmpRateLimitPropertiesTest {

    @Test
    fun `default rate limit per minute is 30`() {
        ApplicationContextRunner()
            .withUserConfiguration(EnableFmpRateLimitProperties::class.java)
            .run { ctx ->
            assertThat(ctx.getBean(FmpRateLimitProperties::class.java).rateLimitPerMinute)
                .isEqualTo(FmpRateLimitProperties.DEFAULT_RATE_LIMIT_PER_MINUTE)
        }
    }

    @Test
    fun `fmp rate-limit-per-minute property overrides default`() {
        ApplicationContextRunner()
            .withUserConfiguration(EnableFmpRateLimitProperties::class.java)
            .withPropertyValues("fmp.rate-limit-per-minute=10")
            .run { ctx ->
                assertThat(ctx.getBean(FmpRateLimitProperties::class.java).rateLimitPerMinute)
                    .isEqualTo(10)
            }
    }

    @Test
    fun `FmpResilienceConfig rate limiter uses injected limit per period`() {
        val config = FmpResilienceConfig(FmpRateLimitProperties(rateLimitPerMinute = 10))
        val limiter = config.fmpRateLimiterRegistry().rateLimiter(FmpResilienceConfig.FMP_INSTANCE)

        assertThat(limiter.rateLimiterConfig.limitForPeriod).isEqualTo(10)
    }

    @EnableConfigurationProperties(FmpRateLimitProperties::class)
    private class EnableFmpRateLimitProperties
}
