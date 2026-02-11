package com.auto.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {
    private int totalTests;
    private int successfulTests;
    private int failedTests;
    private double successRate;
    private Duration avgExecutionTime;
    @Builder.Default
    private Map<String, Integer> issuesBySeverity = new HashMap<>();
    
    public void calculateSuccessRate() {
        if (totalTests > 0) {
            this.successRate = (double) successfulTests / totalTests * 100;
        } else {
            this.successRate = 0.0;
        }
    }
}
