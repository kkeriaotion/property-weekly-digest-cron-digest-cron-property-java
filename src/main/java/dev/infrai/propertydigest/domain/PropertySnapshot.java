package dev.infrai.propertydigest.domain;

import java.time.LocalDate;
import java.util.List;

public record PropertySnapshot(
        List<MaintenanceRequest> maintenanceRequests,
        List<TenantDocument> tenantDocuments,
        List<InspectionReminder> inspectionReminders) {

    public record MaintenanceRequest(String unit, String summary, boolean open) {}
    public record TenantDocument(String tenant, String documentName, LocalDate expiresOn) {}
    public record InspectionReminder(String unit, String inspectionName, LocalDate dueOn) {}
}
