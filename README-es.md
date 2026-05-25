# remote-upload

[![build](https://github.com/calcifux/remote-upload-java/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/calcifux/remote-upload-java/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/calcifux/remote-upload-java.svg)](https://jitpack.io/#calcifux/remote-upload-java)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=calcifux_remote-upload-java&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=calcifux_remote-upload-java)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=calcifux_remote-upload-java&metric=coverage)](https://sonarcloud.io/summary/new_code?id=calcifux_remote-upload-java)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

> Read this in other languages: [English](README.md)

> **Sube contenido local HACIA almacenamiento remoto** (S3 / MinIO, Azure Blob, GCS, SFTP, FTP, HTTP autenticado) con una API chica y agnóstica al framework — el **gemelo de escritura** de [`remote-download`](https://github.com/calcifux/remote-download-java).

```java
UploadResult r = RemoteUpload.to(target)
        .body(inputStream, length)
        .contentType("image/jpeg")
        .upload();
```

Funciona en **Spring Boot**, **Quarkus**, JAX-RS, Servlet, AWS Lambda, CLI — donde puedas leer bytes de un `InputStream`.

## Por qué existe

`remote-download` saca bytes de un origen remoto hacia tu cliente. `remote-upload` es la otra mitad: empuja bytes hacia un destino remoto. Misma forma, espejeada:

| | remote-download | remote-upload |
|---|---|---|
| Puerto | `DownloadOrigin.open() → RemoteContent` | `UploadTarget.upload(UploadContent) → UploadResult` |
| Fachada | `RemoteDownload.from(src).writeTo(out)` | `RemoteUpload.to(target).body(in, len).upload()` |
| Dirección | remoto → tu backend → cliente | cliente → tu backend → remoto |

## Módulos

| Módulo | Qué agrega |
|---|---|
| `remote-upload-core` | El puerto `UploadTarget`, la fachada `RemoteUpload` y un `HttpTarget` (PUT) con el HttpClient del JDK. Solo depende de SLF4J. |
| `remote-upload-apache` | HTTP enterprise con Apache HttpClient 5 (reintentos, NTLM, proxy, timeouts). |
| `remote-upload-s3` | Amazon S3 + compatibles (MinIO, Ceph, LocalStack, GCS XML API) con AWS SDK v2. |
| `remote-upload-azure` | Azure Blob Storage. |
| `remote-upload-gcs` | Google Cloud Storage. |
| `remote-upload-sftp` | SFTP con Apache Mina SSHD. |
| `remote-upload-ftp` | FTP / FTPS con Apache Commons Net. |
| `remote-upload-spring` | Starter de Spring Boot: auto-config, helpers de `MultipartFile`, fachada `Uploads`. |
| `remote-upload-quarkus` | Bean CDI + fachada estática para Quarkus / Helidon / OpenLiberty. |

## Inicio rápido (S3 / MinIO)

```java
UploadTarget target = S3Target.builder()
        .bucket("my-bucket")
        .key("tenant-1/uploads/abc/photo.jpg")
        .endpoint("http://localhost:9000")   // MinIO; omite para AWS real
        .credentials(accessKey, secretKey)
        .build();

UploadResult r = RemoteUpload.to(target)
        .body(inputStream, contentLength)
        .contentType("image/jpeg")
        .checksum("SHA-256")
        .upload();
```

## Conceptos

- **`UploadTarget`** — el puerto: `UploadResult upload(UploadContent)`. Impleméntalo para cualquier destino.
- **`UploadContent`** — el body + metadata (content type, length, filename, metadata libre).
- **`UploadResult`** — key, location, ETag, versionId, bytes, duración y checksum opcional.

### Manejo de errores — reintentable vs terminal

- **`RetryableUploadException`** — transitorio (red, 5xx, timeout): re-encolar con backoff.
- **`TerminalUploadException`** — permanente (auth, 4xx, cuota): no reintentar.

Es la mejora deliberada sobre `remote-download` (que tenía una sola excepción): permite que un outbox/coordinador de sync decida entre "seguir reintentando" y "marcar fallido y avisar".

## Instalar (JitPack)

```xml
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>
<dependency>
  <groupId>com.github.calcifux.remote-upload-java</groupId>
  <artifactId>remote-upload-s3</artifactId>
  <version>v1.0.1</version>
</dependency>
```

## Licencia

MIT © Carlos Guillermo Reyes Ramiro. Ver [LICENSE](LICENSE).
