package com.github.calcifux.remoteupload.gcs;

import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * {@link UploadTarget} that writes objects to Google Cloud Storage. Write-side
 * twin of {@code GcsOrigin}.
 *
 * <p>Credentials resolve in this order:
 * <ol>
 *     <li>An explicit {@link GoogleCredentials} via {@link Builder#credentials(GoogleCredentials)}</li>
 *     <li>A JSON file via {@link Builder#credentialsPath(String)} ({@code classpath:},
 *         {@code file:} or absolute path)</li>
 *     <li>Application Default Credentials (env var, gcloud, metadata server)</li>
 * </ol>
 *
 * <p>For tests / reuse, supply a pre-built {@link Storage} via
 * {@link Builder#storage(Storage)} — an injected client is never closed by this
 * target (the caller owns its lifecycle).
 *
 * @since 1.0.0
 */
@Slf4j
public class GcsTarget implements UploadTarget {

    private final String bucket;
    private final String objectName;
    private final String projectId;
    private final String credentialsPath;
    private final GoogleCredentials credentials;
    private final Storage sharedStorage;

    private GcsTarget(Builder b) {
        this.bucket = b.bucket;
        this.objectName = b.objectName;
        this.projectId = b.projectId;
        this.credentialsPath = b.credentialsPath;
        this.credentials = b.credentials;
        this.sharedStorage = b.sharedStorage;
    }

    /** @return a fluent builder for {@link GcsTarget} */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        log.debug("[GcsTarget] PUT gs://{}/{}", bucket, objectName);

        BlobInfo.Builder info = BlobInfo.newBuilder(BlobId.of(bucket, objectName));
        content.contentType().ifPresent(info::setContentType);
        if (!content.metadata().isEmpty()) {
            info.setMetadata(content.metadata());
        }

        Storage storage = (sharedStorage != null) ? sharedStorage : buildStorage();
        boolean ownsStorage = (sharedStorage == null);
        try {
            // createFrom streams the InputStream into the object (no full buffering).
            Blob blob = storage.createFrom(info.build(), content.getBody());
            return UploadResult.builder()
                    .key(objectName)
                    .location("gs://" + bucket + "/" + objectName)
                    .etag(blob.getEtag())
                    .versionId(blob.getGeneration() != null ? String.valueOf(blob.getGeneration()) : null)
                    .contentType(content.getContentType())
                    .build();
        } catch (StorageException e) {
            int code = e.getCode();
            String msg = "GCS upload gs://" + bucket + "/" + objectName + " failed (code " + code + ")";
            if (!e.isRetryable() && code >= 400 && code < 500) {
                throw new TerminalUploadException(msg, e);
            }
            throw new RetryableUploadException(msg, e);
        } finally {
            if (ownsStorage) {
                try {
                    storage.close();
                } catch (Exception e) {
                    log.debug("[GcsTarget] ignored failure closing storage client", e);
                }
            }
        }
    }

    private Storage buildStorage() throws IOException {
        StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();
        if (projectId != null) {
            optionsBuilder.setProjectId(projectId);
        }
        GoogleCredentials creds = resolveCredentials();
        if (creds != null) {
            optionsBuilder.setCredentials(creds);
        }
        return optionsBuilder.build().getService();
    }

    private GoogleCredentials resolveCredentials() throws IOException {
        if (credentials != null) {
            return credentials;
        }
        if (credentialsPath != null) {
            try (InputStream is = openCredentials(credentialsPath)) {
                return GoogleCredentials.fromStream(is);
            }
        }
        return null; // fall back to Application Default Credentials
    }

    private InputStream openCredentials(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            String cp = path.substring("classpath:".length());
            InputStream is = getClass().getClassLoader().getResourceAsStream(cp);
            if (is == null) {
                throw new IOException("Classpath resource not found: " + cp);
            }
            return is;
        }
        if (path.startsWith("file:")) {
            return new FileInputStream(path.substring("file:".length()));
        }
        return new FileInputStream(path);
    }

    /**
     * Fluent builder for {@link GcsTarget}.
     */
    public static class Builder {
        private String bucket;
        private String objectName;
        private String projectId;
        private String credentialsPath;
        private GoogleCredentials credentials;
        private Storage sharedStorage;

        /** GCS bucket (required). */
        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /** Object name / key within the bucket (required). */
        public Builder object(String objectName) {
            this.objectName = objectName;
            return this;
        }

        /** GCP project id; taken from ADC when omitted. */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /** Service-account JSON file ({@code classpath:}, {@code file:} or absolute path). */
        public Builder credentialsPath(String path) {
            this.credentialsPath = path;
            return this;
        }

        /** Pre-built credentials (vault / programmatic). */
        public Builder credentials(GoogleCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        /** Supplies a pre-built {@link Storage} client (reuse / tests). Never closed by this target. */
        public Builder storage(Storage storage) {
            this.sharedStorage = storage;
            return this;
        }

        /**
         * @return an immutable {@link GcsTarget}
         * @throws IllegalStateException if {@code bucket} or {@code object} is missing
         */
        public GcsTarget build() {
            if (bucket == null || objectName == null) {
                throw new IllegalStateException("GcsTarget: bucket and object are required");
            }
            return new GcsTarget(this);
        }
    }
}
