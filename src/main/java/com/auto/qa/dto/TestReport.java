package com.auto.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestReport {
    private String id;
    private String url;
    private LocalDateTime executedAt;
    private String model;
    private String status;  // SUCCESS, FAILED, PARTIAL
    @Builder.Default
    private List<TestIssue> issues = new ArrayList<>();
    private Duration executionTime;
    private String filePath;
    
    public int getHighIssueCount() {
        return (int) issues.stream()
            .filter(i -> "HIGH".equalsIgnoreCase(i.getSeverity()))
            .count();
    }
    
    public int getMediumIssueCount() {
        return (int) issues.stream()
            .filter(i -> "MEDIUM".equalsIgnoreCase(i.getSeverity()))
            .count();
    }
    
    public int getLowIssueCount() {
        return (int) issues.stream()
            .filter(i -> "LOW".equalsIgnoreCase(i.getSeverity()))
            .count();
    }
}
