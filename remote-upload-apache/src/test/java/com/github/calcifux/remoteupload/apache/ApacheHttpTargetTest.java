package com.github.calcifux.remoteupload.apache;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApacheHttpTargetTest {

    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void put_2xx_returns_result_with_etag() throws Exception {
        server.stubFor(put(urlEqualTo("/up"))
                .willReturn(aResponse().withStatus(200).withHeader("ETag", "\"xyz\"")));

        UploadTarget target = ApacheHttpTarget.url(server.baseUrl() + "/up").retries(0).build();
        UploadResult r = RemoteUpload.to(target)
                .body("hi".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .upload();

        // WireMock gzips the response (Apache HC sends Accept-Encoding: gzip) and
        // appends "--gzip" to the ETag, so assert the substring, not the exact value.
        assertThat(r.etag().orElseThrow()).contains("xyz");
        assertThat(r.getBytesTransferred()).isEqualTo(2);
        server.verify(putRequestedFor(urlEqualTo("/up")));
    }

    @Test
    void put_4xx_is_terminal() {
        server.stubFor(put(urlEqualTo("/up")).willReturn(aResponse().withStatus(403)));
        UploadTarget target = ApacheHttpTarget.url(server.baseUrl() + "/up").retries(0).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(TerminalUploadException.class);
    }

    @Test
    void put_5xx_is_retryable() {
        server.stubFor(put(urlEqualTo("/up")).willReturn(aResponse().withStatus(503)));
        UploadTarget target = ApacheHttpTarget.url(server.baseUrl() + "/up").retries(0).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void bearer_header_is_sent() throws Exception {
        server.stubFor(put(urlEqualTo("/up")).willReturn(aResponse().withStatus(201)));
        UploadTarget target = ApacheHttpTarget.url(server.baseUrl() + "/up")
                .retries(0)
                .bearer("tok")
                .build();

        RemoteUpload.to(target).body(new byte[]{1, 2}).upload();

        server.verify(putRequestedFor(urlEqualTo("/up"))
                .withHeader("Authorization", equalTo("Bearer tok")));
    }

    @Test
    void proxy_auth_before_proxy_is_rejected() {
        assertThatThrownBy(() -> ApacheHttpTarget.url(server.baseUrl() + "/up").proxyAuth("u", "p"))
                .isInstanceOf(IllegalStateException.class);
    }
}
