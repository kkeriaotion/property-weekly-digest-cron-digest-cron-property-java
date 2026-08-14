package dev.infrai.propertydigest.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public class WeeklyDigestComposer {
    private final Clock clock;

    public WeeklyDigestComposer(Clock clock) {
        this.clock = clock;
    }

    public WeeklyDigest compose(PropertySnapshot snapshot) {
        LocalDate today = LocalDate.now(clock);
        List<String> maintenance = snapshot.maintenanceRequests().stream()
                .filter(PropertySnapshot.MaintenanceRequest::open)
                .map(request -> request.unit() + ": " + request.summary())
                .toList();
        List<String> documents = snapshot.tenantDocuments().stream()
                .filter(document -> within(today, document.expiresOn(), 30))
                .map(document -> document.tenant() + ": " + document.documentName() + " (" + document.expiresOn() + ")")
                .toList();
        List<String> inspections = snapshot.inspectionReminders().stream()
                .filter(reminder -> within(today, reminder.dueOn(), 14))
                .map(reminder -> reminder.unit() + ": " + reminder.inspectionName() + " (" + reminder.dueOn() + ")")
                .toList();
        return new WeeklyDigest(maintenance, documents, inspections);
    }

    private boolean within(LocalDate today, LocalDate date, int days) {
        return !date.isBefore(today) && !date.isAfter(today.plusDays(days));
    }
}
