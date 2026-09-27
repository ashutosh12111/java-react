package com.platform.common.web.autoconfigure;

import com.platform.common.web.correlation.CorrelationIdFilter;
import com.platform.common.web.correlation.CorrelationIdPropagationInterceptor;
import com.platform.common.web.correlation.MdcTaskDecorator;
import com.platform.common.web.error.GlobalExceptionHandler;
import com.platform.common.web.openapi.OpenApiConfiguration;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.client.RestClient;

/**
 * Registers the platform's cross-cutting components in every service.
 *
 * <p>Also listed in the {@code @WebMvcTest} imports so controller slice tests see the same error
 * contract and correlation behaviour as the running application.
 */
@AutoConfiguration
public class CommonWebAutoConfiguration {

    /** Injected wherever "now" is needed, so time-dependent logic is testable with a fixed clock. */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Applied by Spring Boot to the auto-configured {@code applicationTaskExecutor}. */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /** Every {@code RestClient} built from the auto-configured builder forwards the correlation ID. */
    @Bean
    @ConditionalOnClass(RestClient.class)
    RestClientCustomizer correlationIdPropagationCustomizer() {
        return builder -> builder.requestInterceptor(new CorrelationIdPropagationInterceptor());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Import(OpenApiConfiguration.class)
    static class ServletWebConfiguration {

        @Bean
        FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
            FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
            // Run first so that every later filter and log statement sees the correlation ID.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }

        @Bean
        @ConditionalOnMissingBean
        GlobalExceptionHandler globalExceptionHandler(Clock clock) {
            return new GlobalExceptionHandler(clock);
        }
    }
}
