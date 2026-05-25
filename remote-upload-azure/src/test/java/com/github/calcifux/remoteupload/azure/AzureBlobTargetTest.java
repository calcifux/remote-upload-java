package com.github.calcifux.remoteupload.azure;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.github.calcifux.remoteupload.RemoteUpload;
import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureBlobTargetTest {

    @SuppressWarnings("unchecked")
    @Test
    void upload_success_returns_etag_versionId_and_location() throws Exception {
        BlobClient client = mock(BlobClient.class);
        BlockBlobItem item = mock(BlockBlobItem.class);
        when(item.getETag()).thenReturn("\"etag-1\"");
        when(item.getVersionId()).thenReturn("v1");
        Response<BlockBlobItem> response = mock(Response.class);
        when(response.getValue()).thenReturn(item);
        when(client.uploadWithResponse(any(BlobParallelUploadOptions.class), any(), any())).thenReturn(response);
        when(client.getBlobUrl()).thenReturn("https://acct.blob.core.windows.net/c/blob");

        AzureBlobTarget target = AzureBlobTarget.builder().container("c").blob("blob").client(client).build();
        UploadResult r = RemoteUpload.to(target)
                .body("hi".getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .upload();

        assertThat(r.getKey()).isEqualTo("blob");
        // Azure expone el ETag verbatim (con comillas), igual que HttpTarget. OptionalAssert
        // .contains() compara por IGUALDAD del valor, no por substring -> va el valor exacto.
        assertThat(r.etag()).contains("\"etag-1\"");
        assertThat(r.versionId()).contains("v1");
        assertThat(r.location()).contains("https://acct.blob.core.windows.net/c/blob");
    }

    @SuppressWarnings("unchecked")
    @Test
    void azure_4xx_is_terminal() {
        BlobClient client = mock(BlobClient.class);
        BlobStorageException ex = mock(BlobStorageException.class);
        when(ex.getStatusCode()).thenReturn(403);
        when(client.uploadWithResponse(any(BlobParallelUploadOptions.class), any(), any())).thenThrow(ex);

        AzureBlobTarget target = AzureBlobTarget.builder().container("c").blob("blob").client(client).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(TerminalUploadException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void azure_5xx_is_retryable() {
        BlobClient client = mock(BlobClient.class);
        BlobStorageException ex = mock(BlobStorageException.class);
        when(ex.getStatusCode()).thenReturn(503);
        when(client.uploadWithResponse(any(BlobParallelUploadOptions.class), any(), any())).thenThrow(ex);

        AzureBlobTarget target = AzureBlobTarget.builder().container("c").blob("blob").client(client).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void generic_runtime_failure_is_retryable() {
        BlobClient client = mock(BlobClient.class);
        when(client.uploadWithResponse(any(BlobParallelUploadOptions.class), any(), any()))
                .thenThrow(new RuntimeException("kaboom"));

        AzureBlobTarget target = AzureBlobTarget.builder().container("c").blob("blob").client(client).build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(RetryableUploadException.class);
    }

    @Test
    void missing_auth_and_client_throws_illegal_state() {
        // No injected client and no connectionString/endpoint → buildClient() rejects.
        AzureBlobTarget target = AzureBlobTarget.builder().container("c").blob("blob").build();

        assertThatThrownBy(() -> RemoteUpload.to(target).body(new byte[]{1}).upload())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builder_requires_container_and_blob() {
        assertThatThrownBy(() -> AzureBlobTarget.builder().blob("b").build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AzureBlobTarget.builder().container("c").build())
                .isInstanceOf(IllegalStateException.class);
    }
}
