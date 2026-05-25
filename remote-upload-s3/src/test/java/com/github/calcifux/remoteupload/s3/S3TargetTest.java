package com.github.calcifux.remoteupload.s3;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3TargetTest {

    @Test
    void put_success_returns_etag_versionId_and_sets_request_fields() throws Exception {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(inv -> {
                    // Drain the body so the metered stream advances (mimics the SDK reading it).
                    RequestBody rb = inv.getArgument(1);
                    rb.contentStreamProvider().newStream().readAllBytes();
                    return PutObjectResponse.builder().eTag("\"abc123\"").versionId("v1").build();
                });

        S3Target target = S3Target.builder().bucket("b").key("k").client(client).build();

        UploadResult r = RemoteUpload.to(target)
                .body("hi".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .metadata("capturedBy", "u1")
                .upload();

        assertThat(r.getKey()).isEqualTo("k");
        assertThat(r.etag()).contains("abc123");            // quotes stripped
        assertThat(r.versionId()).contains("v1");
        assertThat(r.location()).contains("s3://b/k");       // no endpoint → s3:// form
        assertThat(r.getBytesTransferred()).isEqualTo(2);

        ArgumentCaptor<PutObjectRequest> cap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(cap.capture(), any(RequestBody.class));
        assertThat(cap.getValue().bucket()).isEqualTo("b");
        assertThat(cap.getValue().key()).isEqualTo("k");
        assertThat(cap.getValue().contentType()).isEqualTo("text/plain");
        assertThat(cap.getValue().metadata()).containsEntry("capturedBy", "u1");
        assertThat(cap.getValue().contentLength()).isEqualTo(2L);
    }

    @Test
    void unknown_length_buffers_then_uploads() throws Exception {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        S3Target target = S3Target.builder().bucket("b").key("k").client(client).build();

        UploadResult r = RemoteUpload.to(target)
                .body(new ByteArrayInputStream(new byte[]{1, 2, 3}))   // no length
                .upload();

        assertThat(r.getBytesTransferred()).isEqualTo(3);
    }

    @Test
    void s3_4xx_is_terminal() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow((S3Exception) S3Exception.builder().statusCode(403).message("denied").build());

        S3Target target = S3Target.builder().bucket("b").key("k").client(client).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(TerminalUploadException.class);
    }

    @Test
    void s3_5xx_is_retryable() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow((S3Exception) S3Exception.builder().statusCode(500).message("boom").build());

        S3Target target = S3Target.builder().bucket("b").key("k").client(client).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void sdk_client_exception_is_retryable() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("network down"));

        S3Target target = S3Target.builder().bucket("b").key("k").client(client).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void builds_real_client_against_dead_endpoint_is_retryable() {
        // No injected client → exercises buildClient() (real S3Client + endpoint override
        // + path-style). The connection fails fast → mapped to Retryable.
        S3Target target = S3Target.builder()
                .bucket("b").key("k")
                .endpoint("http://localhost:1")
                .credentials("ak", "sk")
                .build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1, 2}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void builder_requires_bucket_and_key() {
        assertThatThrownBy(() -> S3Target.builder().key("k").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> S3Target.builder().bucket("b").build())
                .isInstanceOf(IllegalStateException.class);
    }
}
