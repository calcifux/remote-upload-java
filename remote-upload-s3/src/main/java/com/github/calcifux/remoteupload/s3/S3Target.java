package com.github.calcifux.remoteupload.s3;

import com.github.calcifux.remoteupload.RetryableUploadException;
import com.github.calcifux.remoteupload.TerminalUploadException;
import com.github.calcifux.remoteupload.UploadContent;
import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.net.URI;

/**
 * {@link UploadTarget} that writes objects to Amazon S3 (or any S3-compatible
 * service: MinIO, Ceph, LocalStack, GCS XML API) using the AWS SDK v2.
 * Write-side twin of {@code S3Origin}.
 *
 * <p>Credentials resolve in this order:
 * <ol>
 *     <li>Static credentials via {@link Builder#credentials(String, String)} /
 *         {@link Builder#credentials(AwsCredentialsProvider)}</li>
 *     <li>The default AWS credentials chain (env, {@code ~/.aws/credentials}, IAM)</li>
 * </ol>
 *
 * <p>Use {@link Builder#endpoint(String)} for S3-compatible services (MinIO):
 * path-style access is enabled automatically when an endpoint override is set
 * (otherwise {@code bucket.localhost} virtual-host resolution fails).
 *
 * <p><strong>Client lifecycle:</strong> by default a fresh {@link S3Client} is
 * created per upload and closed afterwards. For high throughput inject a shared
 * client with {@link Builder#client(S3Client)} — then this target never closes it
 * (the caller owns its lifecycle). This is the deliberate improvement over
 * {@code S3Origin}, which always created a client per call.
 *
 * @since 1.0.0
 */
@Slf4j
public class S3Target implements UploadTarget {

    private final String bucket;
    private final String key;
    private final Region region;
    private final URI endpointOverride;
    private final boolean pathStyle;
    private final AwsCredentialsProvider credentials;
    private final S3Client sharedClient;

    private S3Target(Builder b) {
        this.bucket = b.bucket;
        this.key = b.key;
        this.region = b.region;
        this.endpointOverride = b.endpoint != null ? URI.create(b.endpoint) : null;
        this.pathStyle = (b.pathStyle != null) ? b.pathStyle : (b.endpoint != null);
        this.credentials = b.credentials != null ? b.credentials : DefaultCredentialsProvider.create();
        this.sharedClient = b.sharedClient;
    }

    /** @return a fluent builder for {@link S3Target} */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public UploadResult upload(UploadContent content) throws IOException {
        log.debug("[S3Target] PUT s3://{}/{} endpoint={}", bucket, key, endpointOverride);

        S3Client client = (sharedClient != null) ? sharedClient : buildClient();
        boolean ownsClient = (sharedClient == null);
        try {
            PutObjectRequest.Builder req = PutObjectRequest.builder().bucket(bucket).key(key);
            content.contentType().ifPresent(req::contentType);
            content.contentLength().ifPresent(req::contentLength);
            if (!content.metadata().isEmpty()) {
                req.metadata(content.metadata());
            }

            // Known length → stream without buffering. Unknown length → buffer (the
            // S3 single-PUT API needs the size up front).
            RequestBody body = content.contentLength()
                    .map(len -> RequestBody.fromInputStream(content.getBody(), len))
                    .orElseGet(() -> RequestBody.fromBytes(readAll(content)));

            PutObjectResponse resp = client.putObject(req.build(), body);

            return UploadResult.builder()
                    .key(key)
                    .location(locationOf())
                    .etag(stripQuotes(resp.eTag()))
                    .versionId(resp.versionId())
                    .contentType(content.getContentType())
                    .build();
        } catch (S3Exception e) {
            int status = e.statusCode();
            String msg = "S3 PUT " + bucket + "/" + key + " failed (HTTP " + status + ")";
            if (status >= 400 && status < 500) {
                throw new TerminalUploadException(msg, e);
            }
            throw new RetryableUploadException(msg, e);
        } catch (SdkClientException e) {
            // Network / SDK-side failure (connection, DNS, timeout) — worth retrying.
            throw new RetryableUploadException("S3 PUT " + bucket + "/" + key + " network failure", e);
        } finally {
            if (ownsClient) {
                client.close();
            }
        }
    }

    private S3Client buildClient() {
        var cb = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .forcePathStyle(pathStyle);
        if (endpointOverride != null) {
            cb.endpointOverride(endpointOverride);
        }
        return cb.build();
    }

    private String locationOf() {
        if (endpointOverride != null) {
            return endpointOverride + "/" + bucket + "/" + key;
        }
        return "s3://" + bucket + "/" + key;
    }

    private static byte[] readAll(UploadContent content) {
        try {
            return content.getBody().readAllBytes();
        } catch (IOException e) {
            throw new RetryableUploadException("Failed reading body for S3 upload", e);
        }
    }

    private static String stripQuotes(String etag) {
        if (etag == null) {
            return null;
        }
        return etag.replace("\"", "");
    }

    /**
     * Fluent builder for {@link S3Target}.
     */
    public static class Builder {
        private String bucket;
        private String key;
        private Region region = Region.US_EAST_1;
        private String endpoint;
        private Boolean pathStyle;
        private AwsCredentialsProvider credentials;
        private S3Client sharedClient;

        /** Bucket name (required). */
        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /** Object key (required). */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /** AWS region by id (e.g. {@code "us-east-1"}). Defaults to {@code us-east-1}. */
        public Builder region(String region) {
            this.region = Region.of(region);
            return this;
        }

        /** AWS region using the SDK type. */
        public Builder region(Region region) {
            this.region = region;
            return this;
        }

        /** Endpoint override for S3-compatible services (MinIO, Ceph, LocalStack). */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Forces path-style addressing on/off. Defaults to {@code true} when an
         * {@link #endpoint(String)} is set (required by MinIO), {@code false} otherwise.
         */
        public Builder pathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
            return this;
        }

        /** Static credentials. When omitted the default AWS chain is used. */
        public Builder credentials(String accessKey, String secretKey) {
            this.credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey));
            return this;
        }

        /** A custom credentials provider (assume-role, vault, ...). */
        public Builder credentials(AwsCredentialsProvider provider) {
            this.credentials = provider;
            return this;
        }

        /**
         * Supplies a shared, reusable {@link S3Client}. When set, this target uses
         * it for every upload and never closes it (the caller owns its lifecycle).
         */
        public Builder client(S3Client client) {
            this.sharedClient = client;
            return this;
        }

        /**
         * @return an immutable {@link S3Target}
         * @throws IllegalStateException if {@code bucket} or {@code key} is missing
         */
        public S3Target build() {
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalStateException("S3Target: bucket is required");
            }
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("S3Target: key is required");
            }
            return new S3Target(this);
        }
    }
}
