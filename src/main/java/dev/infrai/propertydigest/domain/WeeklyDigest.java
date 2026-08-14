package dev.infrai.propertydigest.domain;

import java.util.List;

public record WeeklyDigest(List<String> maintenance, List<String> documents, List<String> inspections) {
    public String asText(String propertyName) {
        return "Weekly digest for " + propertyName + "\n\n"
                + section("Open maintenance", maintenance)
                + section("Documents expiring within 30 days", documents)
                + section("Inspections due within 14 days", inspections);
    }

    private static String section(String title, List<String> items) {
        String rows = items.isEmpty() ? "- None\n" : items.stream().map(item -> "- " + item + "\n").reduce("", String::concat);
        return title + "\n" + rows + "\n";
    }
}
