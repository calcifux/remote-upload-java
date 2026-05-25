package com.github.calcifux.remoteupload.azure;

import com.azure.core.http.rest.Response;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * {@link UploadTarget} that writes blobs to Azure Blob Storage. Write-side twin
 * of {@code AzureBlobOrigin}.
 *
 * <p>Two authentication modes:
 * <ul>
 *     <li>Connection string — carries endpoint and credentials together</li>
 *     <li>Endpoint + SAS token — for time-bounded shared access</li>
 * </ul>
 *
 * <p>Uploads overwrite an existing blob (idempotent re-PUT of the same key).
 * For tests / advanced reuse, supply a pre-built {@link BlobClient} via
 * {@link Builder#client(BlobClient)}.
 *
 * @since 1.0.0
 */
@Slf4j
public class AzureBlobTarget implements UploadTarget {

    private final String containerName;
    private final String blobName;
    private final String connectionString;
    private final String endpoint;
    private final String sasToken;
    private final BlobClient sharedClient;

    private AzureBlobTarget(Builder b) {
        this.containerName = b.containerName;
        this.blobName = b.blobName;
        this.connectionString = b.connectionString;
        this.endpoint = b.endpoint;
        this.sasToken = b.sasToken;
        this.sharedClient = b.sharedClient;
    }

    /** @return a fluent builder for {@link AzureBlobTarget} */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        log.debug("[AzureBlobTarget] PUT {}/{}", containerName, blobName);

        BlobClient client = (sharedClient != null) ? sharedClient : buildClient();

        BlobParallelUploadOptions options = content.contentLength().isPresent()
                ? new BlobParallelUploadOptions(content.getBody(), content.getContentLength())
                : new BlobParallelUploadOptions(content.getBody());
        content.contentType().ifPresent(ct ->
                options.setHeaders(new BlobHttpHeaders().setContentType(ct)));
        if (!content.metadata().isEmpty()) {
            options.setMetadata(content.metadata());
        }

        try {
            Response<BlockBlobItem> response = client.uploadWithResponse(options, null, Context.NONE);
            BlockBlobItem item = response.getValue();
            return UploadResult.builder()
                    .key(blobName)
                    .location(client.getBlobUrl())
                    .etag(item != null ? item.getETag() : null)
                    .versionId(item != null ? item.getVersionId() : null)
                    .contentType(content.getContentType())
                    .build();
        } catch (BlobStorageException e) {
            int status = e.getStatusCode();
            String msg = "Azure upload " + containerName + "/" + blobName + " failed (HTTP " + status + ")";
            if (status >= 400 && status < 500) {
                throw new TerminalUploadException(msg, e);
            }
            throw new RetryableUploadException(msg, e);
        } catch (RuntimeException e) {
            throw new RetryableUploadException(
                    "Azure upload " + containerName + "/" + blobName + " failed", e);
        }
    }

    private BlobClient buildClient() {
        BlobClientBuilder cb = new BlobClientBuilder()
                .containerName(containerName)
                .blobName(blobName);
        if (connectionString != null) {
            cb.connectionString(connectionString);
        } else if (endpoint != null) {
            cb.endpoint(endpoint);
            if (sasToken != null) {
                cb.sasToken(sasToken);
            }
        } else {
            throw new IllegalStateException(
                    "AzureBlobTarget: either connectionString, endpoint or an injected client must be provided");
        }
        return cb.buildClient();
    }

    /**
     * Fluent builder for {@link AzureBlobTarget}.
     */
    public static class Builder {
        private String containerName;
        private String blobName;
        private String connectionString;
        private String endpoint;
        private String sasToken;
        private BlobClient sharedClient;

        /** Target container (required). */
        public Builder container(String containerName) {
            this.containerName = containerName;
            return this;
        }

        /** Target blob name / key (required). */
        public Builder blob(String blobName) {
            this.blobName = blobName;
            return this;
        }

        /** Full Azure Storage connection string (takes precedence over endpoint/SAS). */
        public Builder connectionString(String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        /** Storage endpoint URL (e.g. {@code https://<account>.blob.core.windows.net}). */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /** SAS token used together with {@link #endpoint(String)}. */
        public Builder sasToken(String sasToken) {
            this.sasToken = sasToken;
            return this;
        }

        /** Supplies a pre-built {@link BlobClient} (reuse / tests). When set, auth options are ignored. */
        public Builder client(BlobClient client) {
            this.sharedClient = client;
            return this;
        }

        /**
         * @return an immutable {@link AzureBlobTarget}
         * @throws IllegalStateException if {@code container} or {@code blob} is missing
         */
        public AzureBlobTarget build() {
            if (containerName == null || blobName == null) {
                throw new IllegalStateException("AzureBlobTarget: container and blob are required");
            }
            return new AzureBlobTarget(this);
        }
    }
}
