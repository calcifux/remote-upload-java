package com.github.calcifux.remoteupload.gcs;

import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadResult;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GcsTargetTest {

    @Test
    void upload_success_returns_etag_generation_and_location() throws Exception {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        when(blob.getEtag()).thenReturn("etag-1");
        when(blob.getGeneration()).thenReturn(42L);
        when(storage.createFrom(any(BlobInfo.class), any(InputStream.class))).thenReturn(blob);

        GcsTarget target = GcsTarget.builder().bucket("b").object("o").storage(storage).build();
        UploadResult r = RemoteUpload.to(target)
                .body("hi".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .upload();

        assertThat(r.getKey()).isEqualTo("o");
        assertThat(r.etag()).contains("etag-1");
        assertThat(r.versionId()).contains("42");
        assertThat(r.location()).contains("gs://b/o");
    }

    @Test
    void gcs_4xx_is_terminal() throws Exception {
        Storage storage = mock(Storage.class);
        when(storage.createFrom(any(BlobInfo.class), any(InputStream.class)))
                .thenThrow(new StorageException(403, "denied"));

        GcsTarget target = GcsTarget.builder().bucket("b").object("o").storage(storage).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(TerminalUploadException.class);
    }

    @Test
    void gcs_5xx_is_retryable() throws Exception {
        Storage storage = mock(Storage.class);
        when(storage.createFrom(any(BlobInfo.class), any(InputStream.class)))
                .thenThrow(new StorageException(500, "boom"));

        GcsTarget target = GcsTarget.builder().bucket("b").object("o").storage(storage).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void missing_classpath_credentials_propagates_ioexception() {
        // No injected storage → buildStorage() → resolveCredentials() → openCredentials(classpath:)
        GcsTarget target = GcsTarget.builder()
                .bucket("b").object("o")
                .credentialsPath("classpath:does-not-exist.json")
                .build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(IOException.class);
    }

    @Test
    void builder_requires_bucket_and_object() {
        assertThatThrownBy(() -> GcsTarget.builder().object("o").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> GcsTarget.builder().bucket("b").build())
                .isInstanceOf(IllegalStateException.class);
    }
}
