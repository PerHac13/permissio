package com.perhac.permissio.observability.config;

import com.perhac.permissio.config.PermissioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry and observability configuration bean.
 * <p>
 * Evaluates {@link PermissioProperties} to configure OpenTelemetry log and trace emission.
 */
@Configuration
public class OpenTelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryConfig.class);

    private final PermissioProperties properties;

    public OpenTelemetryConfig(PermissioProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnProperty(prefix = "permissio.observability.otel.logs", name = "enabled", havingValue = "true")
    public Object otelLogExporterNotification() {
        log.info("OpenTelemetry OTLP log export enabled pointing to: {}",
                properties.getObservability().getOtel().getEndpoint());
        return new Object();
    }
}
