package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot app demonstrating remote-upload. Run it, then POST a file:
 *
 * <pre>
 * curl -F "file=@/path/to/photo.jpg" http://localhost:8080/upload
 * </pre>
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
