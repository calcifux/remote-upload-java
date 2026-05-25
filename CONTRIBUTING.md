# Contributing to remote-upload-java

> Read this in other languages: [Español](CONTRIBUTING-es.md)

Thanks for your interest in contributing! This document explains how to set up the project locally, the conventions we follow, and how to send a Pull Request that has the best chance of being merged.

## Quick start

```bash
git clone https://github.com/calcifux/remote-upload-java.git
cd remote-upload-java
mvn clean install
```

If `mvn clean install` ends with **BUILD SUCCESS** you are ready to develop.

## Requirements

- **Java 21** — the library targets `--release 21`
- **Maven 3.9+** — older Maven works but the build assumes recent plugins
- **Docker** is NOT required: the adapter tests use injected mock clients, not live services
- **Git** with SSH or HTTPS auth to GitHub

## Repository layout

```
remote-upload-java/                        (parent reactor)
├── remote-upload-core                     ← universal API + JDK HttpClient (HttpTarget)
├── remote-upload-apache                   ← HTTP enterprise (HttpClient 5)
├── remote-upload-s3                       ← Amazon S3 / MinIO
├── remote-upload-azure                    ← Azure Blob
├── remote-upload-gcs                      ← Google Cloud Storage
├── remote-upload-sftp                     ← SFTP
├── remote-upload-ftp                      ← FTP / FTPS
├── remote-upload-spring                   ← Spring Boot starter
├── remote-upload-quarkus                  ← Quarkus / CDI
└── examples/spring-boot-demo              ← consumer demo (NOT in reactor)
```

The example app under `examples/` is **not** part of the parent reactor — it consumes the published JitPack artifacts independently.

## Branching and commits

- `main` is always green and deployable. Don't push directly; open a PR.
- Use **feature branches**: `feat/multipart-upload`, `fix/sftp-timeout`, `docs/install-guide`.
- Follow [Conventional Commits](https://www.conventionalcommits.org/) for the subject line:
  - `feat:` new feature · `fix:` bug fix · `docs:` documentation only
  - `refactor:` no behavior change · `test:` tests · `ci:` build tooling · `chore:` housekeeping

## Code style

- Java 21 idioms (`var` for local vars when the type is obvious)
- **Lombok** for boilerplate (`@Getter`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`)
- Javadocs on every public class and public method, in **English**, enterprise tone
- Logs via SLF4J with a `[ClassName]` prefix at DEBUG, English text
- Imports ordered alphabetically (IntelliJ default)

## Adding a new target

To add a new `UploadTarget` implementation:

1. Create a new module `remote-upload-{name}` mirroring the layout of `remote-upload-s3`
2. Add it to the parent pom under `<modules>` (and to `<dependencyManagement>` if a new dep is introduced)
3. Implement `UploadTarget` with a fluent `Builder`. Accept an **injectable client** so the
   target is unit-testable with a mock (no live service)
4. Map provider failures to `RetryableUploadException` (transient) or `TerminalUploadException` (permanent)
5. Add a unit test with a mocked client (success + error mapping + builder validation)
6. Update the README *Modules* table
7. Open a PR

## Tests & coverage

```bash
mvn -B clean install     # same as CI: tests + JaCoCo coverage gate (85% line / 60% branch)
```

The coverage gate is a regression safety net, not the bar of "good enough" — real quality comes from review. Adapters are tested via injected mock clients; thin SDK-wiring lines that can't run without a live service are excluded from coverage.

## Pull Request checklist

- [ ] `mvn clean install` passes locally
- [ ] New files have proper Javadoc + `@since` tag
- [ ] Conventional Commit format in the subject
- [ ] No personal data, secrets, or credentials in code or commit messages
- [ ] If a public API changed, the README was updated
- [ ] If a new module was added, the parent pom and the README *Modules* table were updated

## Reporting bugs

Open a [bug report](https://github.com/calcifux/remote-upload-java/issues/new?template=bug_report.yml) with the module affected, the version, a minimal reproduction, expected vs. actual behavior, and a stack trace if any.

## Suggesting features

Open a [feature request](https://github.com/calcifux/remote-upload-java/issues/new?template=feature_request.yml) describing the use case, current workaround, and a proposed API sketch.

## License

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
