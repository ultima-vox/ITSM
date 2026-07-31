package ru.ultimavox.itsm.platform.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ItsmOpenSearchProperties.class)
class SearchConfiguration {
}
