package com.platform.common.web.openapi;

import com.platform.common.web.correlation.CorrelationId;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Consistent OpenAPI metadata for every service; each service only supplies its description. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OpenAPI platformOpenApi(
            @Value("${spring.application.name}") String applicationName,
            @Value("${platform.openapi.description:}") String description,
            ObjectProvider<BuildProperties> buildProperties) {
        String version = buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse("dev");
        return new OpenAPI().info(new Info().title(applicationName).description(description).version(version));
    }

    /** Documents the optional correlation header on every operation. */
    @Bean
    OperationCustomizer correlationIdHeaderCustomizer() {
        return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
                .name(CorrelationId.HEADER)
                .required(false)
                .description("Optional request correlation ID; generated when absent and echoed on the response.")
                .schema(new StringSchema().pattern("^[A-Za-z0-9._-]{8,64}$")));
    }
}
