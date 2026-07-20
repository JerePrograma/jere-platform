package com.jereplatform.platform.parties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PartySourceExportProperties.class)
class PartySourceExportConfiguration {
}
