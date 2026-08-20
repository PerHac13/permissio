package com.perhac.permissio.observability.config;

import com.perhac.permissio.config.PermissioProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryConfigTest {

    @Test
    @DisplayName("otelLogExporterNotification bean instantiated when enabled")
    void otelLogExporterNotification_instantiates() {
        MockEnvironment env = new MockEnvironment();
        PermissioProperties props = new PermissioProperties(env);
        props.getObservability().getOtel().setEndpoint("http://localhost:4318");

        OpenTelemetryConfig config = new OpenTelemetryConfig(props);
        Object bean = config.otelLogExporterNotification();

        assertThat(bean).isNotNull();
    }
}
