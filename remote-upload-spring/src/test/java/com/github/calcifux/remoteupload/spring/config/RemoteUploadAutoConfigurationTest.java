package com.github.calcifux.remoteupload.spring.config;

import com.github.calcifux.remoteupload.spring.core.RemoteUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteUploadAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RemoteUploadAutoConfiguration.class));

    @Test
    void registers_service_by_default() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(RemoteUploadService.class));
    }

    @Test
    void binds_checksum_property() {
        runner.withPropertyValues("remote-upload.checksum-algorithm=MD5")
                .run(ctx -> assertThat(ctx.getBean(RemoteUploadProperties.class).getChecksumAlgorithm())
                        .isEqualTo("MD5"));
    }

    @Test
    void backs_off_when_disabled() {
        runner.withPropertyValues("remote-upload.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RemoteUploadService.class));
    }
}
