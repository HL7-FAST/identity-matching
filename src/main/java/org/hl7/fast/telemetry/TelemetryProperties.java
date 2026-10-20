package org.hl7.fast.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Telemetry settings under the "telemetry" prefix in application.yaml
 */
@Configuration
@ConfigurationProperties(prefix = "telemetry")
public class TelemetryProperties {
	@Getter
	@Setter
	private boolean captureQueryStrings = false;
}
