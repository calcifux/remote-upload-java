# remote-upload

[![build](https://github.com/calcifux/remote-upload-java/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/calcifux/remote-upload-java/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/calcifux/remote-upload-java.svg)](https://jitpack.io/#calcifux/remote-upload-java)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

> Read this in other languages: [Español](README-es.md)

> **Stream local content INTO remote storage** (S3 / MinIO, Azure Blob, GCS, SFTP, FTP, authenticated HTTP) through one tiny, framework-agnostic API — the **write-side twin** of [`remote-download`](https://github.com/calcifux/remote-download-java).

```java
UploadResult r = RemoteUpload.to(target)
        .body(inputStream, length)
        .contentType("image/jpeg")
        .upload();
```

Works in **Spring Boot**, **Quarkus**, JAX-RS, plain Servlet, AWS Lambda, CLI scripts — anywhere you can read bytes from an `InputStream`.

## Why it exists

`remote-download` pipes bytes *out* of a remote origin to your client. `remote-upload` is the other half: it pushes bytes *into* a remote destination. Same shape, mirrored:

| | remote-download | remote-upload |
|---|---|---|
| Port | `DownloadOrigin.open() → RemoteContent` | `UploadTarget.upload(UploadContent) → UploadResult` |
| Facade | `RemoteDownload.from(src).writeTo(out)` | `RemoteUpload.to(target).body(in, len).upload()` |
| Direction | remote → your backend → client | client → your backend → remote |

## Modules

| Module | What it adds |
|---|---|
| `remote-upload-core` | The `UploadTarget` port, the `RemoteUpload` facade, and a JDK-`HttpClient` `HttpTarget` (PUT). Only depends on SLF4J. |
| `remote-upload-apache` | Enterprise HTTP via Apache HttpClient 5 (retries, NTLM, proxy auth, granular timeouts). |
| `remote-upload-s3` | Amazon S3 + S3-compatible (MinIO, Ceph, LocalStack, GCS XML API) via AWS SDK v2. |
| `remote-upload-azure` | Azure Blob Storage. |
| `remote-upload-gcs` | Google Cloud Storage. |
| `remote-upload-sftp` | SFTP via Apache Mina SSHD. |
| `remote-upload-ftp` | FTP / FTPS via Apache Commons Net. |
| `remote-upload-spring` | Spring Boot starter: auto-config, `MultipartFile` helpers, static `Uploads` facade. |
| `remote-upload-quarkus` | CDI bean + static facade for Quarkus / Helidon / OpenLiberty. |

## Quick start

### Plain Java — upload to S3 / MinIO

```java
UploadTarget target = S3Target.builder()
        .bucket("my-bucket")
        .key("tenant-1/uploads/abc/photo.jpg")
        .endpoint("http://localhost:9000")   // MinIO; omit for real AWS
        .credentials(accessKey, secretKey)
        .build();

UploadResult r = RemoteUpload.to(target)
        .body(inputStream, contentLength)
        .contentType("image/jpeg")
        .metadata("capturedBy", "user-1")
        .checksum("SHA-256")
        .upload();

System.out.println(r.getKey() + " etag=" + r.etag().orElse("?") + " " + r.getBytesTransferred() + " bytes");
```

### Plain HTTP PUT

```java
RemoteUpload.to("https://api.example.com/files/report.pdf")
        .body(bytes)
        .contentType("application/pdf")
        .upload();
```

### Spring Boot

```java
@RestController
@RequiredArgsConstructor
class FilesController {
    private final RemoteUploadService uploads;   // auto-configured bean

    @PostMapping("/upload")
    Map<String, Object> upload(@RequestParam MultipartFile file) throws IOException {
        UploadTarget target = S3Target.builder().bucket("my-bucket")
                .key("demo/" + file.getOriginalFilename())
                .endpoint("http://localhost:9000").credentials(ak, sk).build();
        UploadResult r = uploads.upload(target, file);   // carries content type + filename
        return Map.of("key", r.getKey(), "bytes", r.getBytesTransferred());
    }
}
```

```yaml
remote-upload:
  enabled: true
  checksum-algorithm: SHA-256   # optional default checksum
```

### Quarkus / Helidon / OpenLiberty

```java
@Inject RemoteUploadCdiService uploads;

UploadResult save(byte[] bytes, UploadTarget target) throws IOException {
    return uploads.upload(target, bytes, "application/pdf");
}
```

## Concepts

- **`UploadTarget`** — the port. One method: `UploadResult upload(UploadContent)`. Implement it for any destination.
- **`UploadContent`** — the body stream + metadata (content type, length, filename, user metadata). Built by the facade.
- **`UploadResult`** — key, location, ETag, version id, bytes transferred, duration, and an optional checksum.
- **`ProgressListener`** — fired as the destination reads the body (`onProgress(sent, total)`).

### Error handling — retryable vs terminal

Targets translate failures into one of two unchecked exceptions so callers can act without parsing messages:

- **`RetryableUploadException`** — transient (network, 5xx, timeout). Re-enqueue with backoff.
- **`TerminalUploadException`** — permanent (auth, 4xx, quota, validation). Don't retry; fix something.

This is the deliberate improvement over `remote-download`'s single exception type: it lets an offline outbox / sync coordinator decide between "keep retrying" and "mark failed, surface to user".

## Install (JitPack)

```xml
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>

<dependency>
  <groupId>com.github.calcifux.remote-upload-java</groupId>
  <artifactId>remote-upload-s3</artifactId>
  <version>v1.0.0</version>
</dependency>
```

Pull only the modules you use; `remote-upload-core` comes transitively.

## What's improved over remote-download

- **Build logs are git-ignored from commit 1** (`*.log`) — no committed `build-vN.log`.
- **Injectable/shared clients** (e.g. `S3Target.builder().client(sharedS3Client)`) to avoid creating a client per upload.
- **Retryable/terminal exception split** for retry-aware callers.

## License

MIT © Carlos Guillermo Reyes Ramiro. See [LICENSE](LICENSE).
