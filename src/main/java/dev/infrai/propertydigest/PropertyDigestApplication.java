package dev.infrai.propertydigest;

import dev.infrai.propertydigest.config.DigestProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DigestProperties.class)
public class PropertyDigestApplication {
    public static void main(String[] args) {
        SpringApplication.run(PropertyDigestApplication.class, args);
    }
}
