## remote-upload v1.0.0 — initial release

Write-side twin of [`remote-download`](https://github.com/calcifux/remote-download-java): stream local content **into** remote storage (S3 / MinIO, Azure Blob, GCS, SFTP, FTP, authenticated HTTP) through one tiny, framework-agnostic API.

```java
UploadResult r = RemoteUpload.to(target)
        .body(inputStream, length)
        .contentType("image/jpeg")
        .upload();
```

### Added

- **remote-upload-core** — `UploadTarget` port, `RemoteUpload` facade, `RemoteUploadRequest` builder, `UploadContent` / `UploadResult`, `ProgressListener`, `MeteredInputStream`, and a JDK-`HttpClient` `HttpTarget` (PUT/POST). Only depends on SLF4J.
- **remote-upload-apache** — `ApacheHttpTarget` (HttpClient 5: retries, NTLM, proxy auth, granular timeouts).
- **remote-upload-s3** — `S3Target` (AWS SDK v2; endpoint override for MinIO; injectable `S3Client`).
- **remote-upload-azure** — `AzureBlobTarget` (connection string or endpoint + SAS).
- **remote-upload-gcs** — `GcsTarget` (ADC, service-account JSON, or explicit credentials).
- **remote-upload-sftp** — `SftpTarget` (password or public-key auth).
- **remote-upload-ftp** — `FtpTarget` (FTP / FTPS, passive mode).
- **remote-upload-spring** — Spring Boot starter: auto-config, `MultipartFile` helpers, static `Uploads` facade.
- **remote-upload-quarkus** — `RemoteUploadCdiService` (CDI) + static `Uploads` facade.
- Retryable / terminal exception split (`RetryableUploadException` / `TerminalUploadException`) for retry-aware callers.
- Optional per-upload checksum (`MD5` / `SHA-256` / ...) surfaced in `UploadResult`.

### Tests

- **69 tests green** across core (17), apache (5), s3 (7), azure (6), gcs (5), sftp (7), ftp (7), spring (9), quarkus (6). JaCoCo coverage report is wired in the parent pom; the check is present but non-blocking for v1.0.0 — the adapter modules get more tests in a follow-up patch.

### Notes

- Single-PUT uploads in v1; multipart / resumable uploads are on the roadmap.

### Install

```xml
<dependency>
  <groupId>com.github.calcifux.remote-upload-java</groupId>
  <artifactId>remote-upload-s3</artifactId>
  <version>v1.0.0</version>
</dependency>
```

Pull only the modules you use; `remote-upload-core` comes transitively. Available on [JitPack](https://jitpack.io/#calcifux/remote-upload-java).
