package dev.infrai.propertydigest.cron;

import dev.infrai.propertydigest.config.DigestProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ScheduleRegistration implements ApplicationRunner {
    private final InfraiCronClient infrai;
    private final DigestProperties properties;

    public ScheduleRegistration(InfraiCronClient infrai, DigestProperties properties) {
        this.infrai = infrai;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.containsOption("register-digest")) {
            String jobId = infrai.create(properties.cronExpr(), properties.taskUrl()); // infrai.cron.create
            System.out.println("Weekly digest scheduled: " + jobId);
        }
    }
}
