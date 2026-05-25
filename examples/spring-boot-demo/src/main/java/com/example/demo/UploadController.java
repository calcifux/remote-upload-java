package com.example.demo;

import com.github.calcifux.remoteupload.UploadResult;
import com.github.calcifux.remoteupload.UploadTarget;
import com.github.calcifux.remoteupload.s3.S3Target;
import com.github.calcifux.remoteupload.spring.core.RemoteUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Demonstration controller. {@code POST /upload} streams an uploaded multipart
 * file straight into an S3-compatible bucket (MinIO by default) through this
 * backend, using the auto-configured {@link RemoteUploadService} bean.
 *
 * <p>Configure the target via {@code application.properties} (bucket, endpoint,
 * credentials).
 */
@RestController
public class UploadController {

    private final RemoteUploadService uploads;

    @Value("${demo.s3.bucket:my-bucket}")
    private String bucket;
    @Value("${demo.s3.endpoint:http://localhost:9000}")
    private String endpoint;
    @Value("${demo.s3.access-key:my-access-key}")
    private String accessKey;
    @Value("${demo.s3.secret-key:CHANGE_ME}")
    private String secretKey;

    public UploadController(RemoteUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        UploadTarget target = S3Target.builder()
                .bucket(bucket)
                .key("demo/" + file.getOriginalFilename())
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        UploadResult r = uploads.upload(target, file);

        return Map.of(
                "key", r.getKey(),
                "etag", r.etag().orElse(""),
                "bytes", r.getBytesTransferred());
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
