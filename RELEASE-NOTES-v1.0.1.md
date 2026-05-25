## remote-upload v1.0.1 — SonarCloud + quality pass

Quality-focused patch. **No public API changes vs v1.0.0** — same
`RemoteUpload.to(target).body(in, len).upload()` surface; internal cleanups plus a
CI quality gate. Drop-in upgrade.

### Changed

- **SonarCloud** wired into CI: Quality Gate **"Sonar way" passing**, ratings **A**
  across Security / Reliability / Maintainability, **0 open issues**, 0 unreviewed
  hotspots, ~81% coverage. Quality Gate + Coverage badges added to the READMEs.
- **Azure** — replaced the deprecated `BlobParallelUploadOptions(InputStream, long)`
  constructor with `BinaryData.fromStream(...)`.
- **Apache** — `CloseableHttpClient` is now managed with try-with-resources.
- **Quarkus** — `RemoteUploadCdiService` switched to constructor injection.
- **SFTP** — `DefaultSftpConnector` closes the `SftpClient` on the connect-failure
  path; authentication extracted to a helper method.

### Install

```xml
<dependency>
  <groupId>com.github.calcifux.remote-upload-java</groupId>
  <artifactId>remote-upload-s3</artifactId>
  <version>v1.0.1</version>
</dependency>
```

Available on [JitPack](https://jitpack.io/#calcifux/remote-upload-java).

### Full changelog

[v1.0.0 → v1.0.1](https://github.com/calcifux/remote-upload-java/compare/v1.0.0...v1.0.1)
