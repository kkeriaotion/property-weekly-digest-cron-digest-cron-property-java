package dev.infrai.propertydigest.web;

import dev.infrai.propertydigest.config.DigestProperties;
import dev.infrai.propertydigest.domain.PropertySnapshot;
import dev.infrai.propertydigest.domain.WeeklyDigest;
import dev.infrai.propertydigest.domain.WeeklyDigestComposer;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/digests/weekly")
public class WeeklyDigestEndpoint {
    private final WeeklyDigestComposer composer;
    private final JavaMailSender mailSender;
    private final DigestProperties properties;

    public WeeklyDigestEndpoint(WeeklyDigestComposer composer, JavaMailSender mailSender, DigestProperties properties) {
        this.composer = composer;
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @PostMapping("/send")
    public Map<String, Object> send() {
        LocalDate today = LocalDate.now();
        PropertySnapshot snapshot = new PropertySnapshot(
                List.of(new PropertySnapshot.MaintenanceRequest("2B", "Kitchen tap repair", true)),
                List.of(new PropertySnapshot.TenantDocument("A. Rivera", "Insurance certificate", today.plusDays(18))),
                List.of(new PropertySnapshot.InspectionReminder("4A", "Smoke alarm inspection", today.plusDays(7))));
        WeeklyDigest digest = composer.compose(snapshot);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(properties.recipient());
        message.setSubject(properties.propertyName() + " weekly property digest");
        message.setText(digest.asText(properties.propertyName()));
        mailSender.send(message);
        return Map.of("sent", true, "recipient", properties.recipient(), "includedItems",
                digest.maintenance().size() + digest.documents().size() + digest.inspections().size());
    }
}
