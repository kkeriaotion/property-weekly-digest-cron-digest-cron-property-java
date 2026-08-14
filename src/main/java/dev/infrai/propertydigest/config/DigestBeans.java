package dev.infrai.propertydigest.config;

import dev.infrai.propertydigest.domain.WeeklyDigestComposer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class DigestBeans {
    @Bean WeeklyDigestComposer weeklyDigestComposer() { return new WeeklyDigestComposer(Clock.systemUTC()); }

    @Bean HttpClient infraiHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
}
