package org.hl7.fast.config;

import org.hl7.fast.operations.models.IdentityMatchValidationLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Identity-matching settings under the "hapi.fhir" prefix in application.yaml
 */
@Configuration
@ConfigurationProperties(prefix = "hapi.fhir")
public class IdentityMatchingProperties {
	@Getter
	@Setter
	private List<String> initialData = new ArrayList<>();

	@Getter
	@Setter
	private String matchValidationHeader = "X-Match-Validation";

	@Getter
	@Setter
	private IdentityMatchValidationLevel matchValidationLevel = IdentityMatchValidationLevel.DEFAULT;

	@Getter
	@Setter
	private String remoteMatchHeader = "X-Remote-Match";

	@Getter
	@Setter
	private List<String> remoteServers = new ArrayList<>();

	@Getter
	@Setter
	private Integer remoteLimit = 3;
}
