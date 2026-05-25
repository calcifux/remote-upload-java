# Changelog

> Read this in other languages: [Español](CHANGELOG-es.md)

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-05-25

### Changed

- **SonarCloud** analysis wired into CI (Quality Gate "Sonar way" — passing, all
  ratings A, 0 open issues); added Quality Gate + Coverage badges to the READMEs.
- **`remote-upload-azure`** — replaced the deprecated
  `BlobParallelUploadOptions(InputStream, long)` constructor with `BinaryData.fromStream(...)`.
- **`remote-upload-apache`** — the `CloseableHttpClient` is now managed with try-with-resources.
- **`remote-upload-quarkus`** — `RemoteUploadCdiService` uses constructor injection.
- **`remote-upload-sftp`** — `DefaultSftpConnector` closes the `SftpClient` on the
  connect-failure path; authentication extracted to a helper method.

### Notes

- No public API changes vs 1.0.0 — internal quality / build improvements only. Drop-in upgrade.

## [1.0.0] - 2026-05-25

### Added

- **`remote-upload-core`** — `UploadTarget` port, `RemoteUpload` facade,
  `RemoteUploadRequest` fluent builder, `UploadContent`, `UploadResult`,
  `ProgressListener`, and a JDK-`HttpClient` `HttpTarget` (PUT/POST).
- **`remote-upload-apache`** — `ApacheHttpTarget` (HttpClient 5: retries, NTLM,
  proxy auth, granular timeouts).
- **`remote-upload-s3`** — `S3Target` (AWS SDK v2; endpoint override for MinIO;
  optional injectable/shared `S3Client`).
- **`remote-upload-azure`** — `AzureBlobTarget` (connection string or endpoint + SAS).
- **`remote-upload-gcs`** — `GcsTarget` (ADC, service-account JSON, or explicit credentials).
- **`remote-upload-sftp`** — `SftpTarget` (password or public-key auth).
- **`remote-upload-ftp`** — `FtpTarget` (FTP / FTPS, passive mode).
- **`remote-upload-spring`** — Spring Boot starter: auto-configuration,
  `RemoteUploadProperties`, `RemoteUploadService` (`MultipartFile` helpers) and
  static `Uploads` facade.
- **`remote-upload-quarkus`** — `RemoteUploadCdiService` (CDI) + static `Uploads` facade.
- Retryable/terminal exception split (`RetryableUploadException` /
  `TerminalUploadException`) for retry-aware callers.
- Optional per-upload checksum (`MD5` / `SHA-256` / ...) surfaced in `UploadResult`.

### Notes

- Write-side twin of `remote-download`. Single-PUT uploads in v1; multipart /
  resumable uploads are on the roadmap.

[1.0.1]: https://github.com/calcifux/remote-upload-java/releases/tag/v1.0.1
[1.0.0]: https://github.com/calcifux/remote-upload-java/releases/tag/v1.0.0
