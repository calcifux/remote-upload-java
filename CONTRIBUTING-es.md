# Contribuir a remote-upload-java

> Read this in other languages: [English](CONTRIBUTING.md)

¡Gracias por tu interés en contribuir! Este documento explica cómo levantar el proyecto en local, las convenciones que seguimos y cómo enviar un Pull Request con las mejores probabilidades de merge.

## Inicio rápido

```bash
git clone https://github.com/calcifux/remote-upload-java.git
cd remote-upload-java
mvn clean install
```

Si `mvn clean install` termina en **BUILD SUCCESS**, ya puedes desarrollar.

## Requisitos

- **Java 21** — la librería compila con `--release 21`
- **Maven 3.9+**
- **Docker NO** es necesario: los tests de los adapters usan clients mock inyectados, no servicios vivos
- **Git** con auth SSH o HTTPS a GitHub

## Estructura del repo

```
remote-upload-java/                        (reactor padre)
├── remote-upload-core                     ← API universal + HttpClient del JDK (HttpTarget)
├── remote-upload-apache                   ← HTTP enterprise (HttpClient 5)
├── remote-upload-s3                       ← Amazon S3 / MinIO
├── remote-upload-azure                    ← Azure Blob
├── remote-upload-gcs                      ← Google Cloud Storage
├── remote-upload-sftp                     ← SFTP
├── remote-upload-ftp                      ← FTP / FTPS
├── remote-upload-spring                   ← Starter de Spring Boot
├── remote-upload-quarkus                  ← Quarkus / CDI
└── examples/spring-boot-demo              ← demo consumidor (NO en el reactor)
```

## Ramas y commits

- `main` siempre verde y deployable. No hagas push directo; abre un PR.
- Ramas de feature: `feat/multipart-upload`, `fix/sftp-timeout`, `docs/install-guide`.
- [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `ci:`, `chore:`.

## Estilo de código

- Java 21 (`var` cuando el tipo es obvio)
- **Lombok** para boilerplate (`@Getter`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`)
- Javadoc en toda clase/método público, en **inglés**, tono enterprise
- Logs por SLF4J con prefijo `[ClassName]` en DEBUG
- Imports ordenados alfabéticamente

## Agregar un target nuevo

1. Crea el módulo `remote-upload-{nombre}` espejeando `remote-upload-s3`
2. Agrégalo al `<modules>` del parent (y a `<dependencyManagement>` si trae dep nueva)
3. Implementa `UploadTarget` con `Builder` fluido. Acepta un **client inyectable** para poder testear con mock (sin servicio vivo)
4. Mapea fallos a `RetryableUploadException` (transitorio) o `TerminalUploadException` (permanente)
5. Test unitario con client mock (éxito + mapeo de errores + validación del builder)
6. Actualiza la tabla *Modules* del README
7. Abre el PR

## Tests y cobertura

```bash
mvn -B clean install     # igual que CI: tests + gate JaCoCo (85% línea / 60% rama)
```

El gate es una red de seguridad anti-regresión, no la barra de "suficiente" — la calidad real la pone el code review.

## Checklist del PR

- [ ] `mvn clean install` pasa en local
- [ ] Javadoc + `@since` en lo nuevo
- [ ] Subject en Conventional Commits
- [ ] Sin datos personales, secretos ni credenciales
- [ ] Si cambió API pública, se actualizó el README
- [ ] Si se agregó módulo, se actualizó el parent + la tabla *Modules*

## Licencia

Al contribuir aceptas que tu aporte queda bajo la [Licencia MIT](LICENSE).
