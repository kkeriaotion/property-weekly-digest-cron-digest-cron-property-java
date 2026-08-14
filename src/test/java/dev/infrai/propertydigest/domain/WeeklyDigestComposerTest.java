package dev.infrai.propertydigest.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyDigestComposerTest {
    private final LocalDate today = LocalDate.of(2026, 8, 14);
    private final WeeklyDigestComposer composer = new WeeklyDigestComposer(
            Clock.fixed(Instant.parse("2026-08-14T08:00:00Z"), ZoneOffset.UTC));

    @Test
    void includes_actionable_items_and_leaves_later_or_closed_work_for_another_week() {
        PropertySnapshot snapshot = new PropertySnapshot(
                List.of(
                        new PropertySnapshot.MaintenanceRequest("2B", "Leaking tap", true),
                        new PropertySnapshot.MaintenanceRequest("1A", "Repaint hall", false)),
                List.of(
                        new PropertySnapshot.TenantDocument("Mina", "Insurance", today.plusDays(30)),
                        new PropertySnapshot.TenantDocument("Theo", "Pet agreement", today.plusDays(31))),
                List.of(
                        new PropertySnapshot.InspectionReminder("3C", "Smoke alarm", today.plusDays(14)),
                        new PropertySnapshot.InspectionReminder("5D", "Balcony", today.plusDays(15))));

        WeeklyDigest digest = composer.compose(snapshot);

        assertThat(digest.maintenance()).containsExactly("2B: Leaking tap");
        assertThat(digest.documents()).containsExactly("Mina: Insurance (2026-09-13)");
        assertThat(digest.inspections()).containsExactly("3C: Smoke alarm (2026-08-28)");
    }
}
