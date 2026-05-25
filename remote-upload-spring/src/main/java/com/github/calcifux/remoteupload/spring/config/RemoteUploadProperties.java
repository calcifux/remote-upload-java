package com.github.calcifux.remoteupload.spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the Spring Boot starter, bound from properties
 * prefixed with {@code remote-upload}.
 *
 * <pre>{@code
 * remote-upload:
 *   enabled: true
 *   checksum-algorithm: SHA-256   # optional; omit to skip checksums
 * }</pre>
 *
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remote-upload")
public class RemoteUploadProperties {

    /** Toggles the auto-configuration. Defaults to {@code true}. */
    private boolean enabled = true;

    /**
     * Optional default checksum algorithm (e.g. {@code "SHA-256"}, {@code "MD5"})
     * applied by {@link com.github.calcifux.remoteupload.spring.core.RemoteUploadService}
     * to every upload. {@code null} (default) disables checksums.
     */
    private String checksumAlgorithm;
}
