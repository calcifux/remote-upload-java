package com.github.calcifux.remoteupload.spring.config;

import com.github.calcifux.remoteupload.spring.core.RemoteUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for {@code remote-upload}.
 *
 * <p>Registers a {@link RemoteUploadService} singleton driven by
 * {@link RemoteUploadProperties}. Gated by {@code remote-upload.enabled};
 * absence of the property is treated as enabled (zero-config). Applications can
 * override by exposing their own {@link RemoteUploadService} bean.
 *
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RemoteUploadProperties.class)
@ConditionalOnProperty(prefix = "remote-upload", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RemoteUploadAutoConfiguration {

    /**
     * Builds and registers the default {@link RemoteUploadService}.
     *
     * @param properties bound configuration
     * @return service ready to be injected by the application
     */
    @Bean
    @ConditionalOnMissingBean(RemoteUploadService.class)
    public RemoteUploadService remoteUploadService(RemoteUploadProperties properties) {
        log.info("[RemoteUploadAutoConfiguration] Registering RemoteUploadService checksumAlgorithm={}",
                properties.getChecksumAlgorithm());
        return new RemoteUploadService(properties);
    }
}
