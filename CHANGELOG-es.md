# Changelog

> Léelo en otros idiomas: [English](CHANGELOG.md)

Todos los cambios relevantes del proyecto se documentan aquí. El formato se basa en
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) y el proyecto sigue
[Versionado Semántico](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-05-25

### Agregado

- **`remote-upload-core`** — puerto `UploadTarget`, fachada `RemoteUpload`,
  builder `RemoteUploadRequest`, `UploadContent`, `UploadResult`,
  `ProgressListener`, `MeteredInputStream` y un `HttpTarget` (PUT/POST) con el
  `HttpClient` del JDK. Solo depende de SLF4J.
- **`remote-upload-apache`** — `ApacheHttpTarget` (HttpClient 5: reintentos, NTLM,
  proxy auth, timeouts granulares).
- **`remote-upload-s3`** — `S3Target` (AWS SDK v2; override de endpoint para MinIO;
  `S3Client` inyectable opcional).
- **`remote-upload-azure`** — `AzureBlobTarget` (connection string o endpoint + SAS).
- **`remote-upload-gcs`** — `GcsTarget` (ADC, JSON de service-account o credenciales explícitas).
- **`remote-upload-sftp`** — `SftpTarget` (auth por password o llave pública).
- **`remote-upload-ftp`** — `FtpTarget` (FTP / FTPS, modo pasivo).
- **`remote-upload-spring`** — starter de Spring Boot: auto-config,
  `RemoteUploadProperties`, `RemoteUploadService` (helpers de `MultipartFile`) y
  fachada estática `Uploads`.
- **`remote-upload-quarkus`** — `RemoteUploadCdiService` (CDI) + fachada estática `Uploads`.
- Separación de excepciones retryable/terminal (`RetryableUploadException` /
  `TerminalUploadException`) para callers conscientes de reintentos.
- Checksum opcional por subida (`MD5` / `SHA-256` / ...) expuesto en `UploadResult`.

### Notas

- Gemelo de escritura de `remote-download`. Subidas single-PUT en v1; subidas
  multipart / resumibles están en el roadmap.

[1.0.0]: https://github.com/calcifux/remote-upload-java/releases/tag/v1.0.0
