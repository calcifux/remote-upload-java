package com.github.calcifux.remoteupload.http;

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

class HttpTargetTest {

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
    void put_2xx_returns_result_with_etag_and_sends_content_type() throws Exception {
        server.stubFor(put(urlEqualTo("/up"))
                .willReturn(aResponse().withStatus(200).withHeader("ETag", "\"abc123\"")));

        UploadTarget target = HttpTarget.url(server.baseUrl() + "/up").build();
        UploadResult r = RemoteUpload.to(target)
                .body("hi".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .upload();

        assertThat(r.getKey()).endsWith("/up");
        assertThat(r.etag()).contains("\"abc123\"");
        server.verify(putRequestedFor(urlEqualTo("/up"))
                .withHeader("Content-Type", equalTo("text/plain")));
    }

    @Test
    void put_4xx_is_terminal() {
        server.stubFor(put(urlEqualTo("/up")).willReturn(aResponse().withStatus(403)));
        UploadTarget target = HttpTarget.url(server.baseUrl() + "/up").build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(TerminalUploadException.class);
    }

    @Test
    void put_5xx_is_retryable() {
        server.stubFor(put(urlEqualTo("/up")).willReturn(aResponse().withStatus(503)));
        UploadTarget target = HttpTarget.url(server.baseUrl() + "/up").build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void network_error_is_retryable() {
        // Nothing listening on this port → connection failure.
        UploadTarget target = HttpTarget.url("http://localhost:1/up").build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void bearer_and_custom_header_are_sent() throws Exception {
        server.stubFor(put(urlEqualTo("/up")).willReturn(aResponse().withStatus(201)));
        UploadTarget target = HttpTarget.url(server.baseUrl() + "/up")
                .bearer("tok")
                .header("X-Foo", "bar")
                .build();

        RemoteUpload.to(target).body(new byte[]{1, 2}).upload();

        server.verify(putRequestedFor(urlEqualTo("/up"))
                .withHeader("Authorization", equalTo("Bearer tok"))
                .withHeader("X-Foo", equalTo("bar")));
    }

    @Test
    void blank_url_is_rejected() {
        assertThatThrownBy(() -> HttpTarget.url("  ").build())
                .isInstanceOf(IllegalStateException.class);
    }
}
