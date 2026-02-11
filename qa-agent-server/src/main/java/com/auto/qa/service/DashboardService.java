package com.auto.qa.service;

import com.auto.qa.dto.ChartData;
import com.auto.qa.dto.DashboardStats;
import com.auto.qa.dto.TestReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final ReportParserService reportParserService;
    private static final String REPORT_DIR = "qa-prompts/report";
    
    public DashboardStats getStats() {
        List<TestReport> reports = getAllReports();
        
        DashboardStats stats = DashboardStats.builder()
            .totalTests(reports.size())
            .build();
        
        int successCount = 0;
        int failedCount = 0;
        long totalExecutionSeconds = 0;
        Map<String, Integer> issuesBySeverity = new HashMap<>();
        issuesBySeverity.put("HIGH", 0);
        issuesBySeverity.put("MEDIUM", 0);
        issuesBySeverity.put("LOW", 0);
        
        for (TestReport report : reports) {
            if ("SUCCESS".equalsIgnoreCase(report.getStatus())) {
                successCount++;
            } else {
                failedCount++;
            }
            
            if (report.getExecutionTime() != null) {
                totalExecutionSeconds += report.getExecutionTime().getSeconds();
            }
            
            issuesBySeverity.put("HIGH", issuesBySeverity.get("HIGH") + report.getHighIssueCount());
            issuesBySeverity.put("MEDIUM", issuesBySeverity.get("MEDIUM") + report.getMediumIssueCount());
            issuesBySeverity.put("LOW", issuesBySeverity.get("LOW") + report.getLowIssueCount());
        }
        
        stats.setSuccessfulTests(successCount);
        stats.setFailedTests(failedCount);
        stats.calculateSuccessRate();
        
        if (reports.size() > 0) {
            stats.setAvgExecutionTime(Duration.ofSeconds(totalExecutionSeconds / reports.size()));
        } else {
            stats.setAvgExecutionTime(Duration.ZERO);
        }
        
        stats.setIssuesBySeverity(issuesBySeverity);
        
        return stats;
    }
    
    public List<TestReport> getReports(int page, int size) {
        List<TestReport> allReports = getAllReports();
        
        // Sort by executedAt descending (most recent first)
        allReports.sort((r1, r2) -> {
            if (r1.getExecutedAt() == null) return 1;
            if (r2.getExecutedAt() == null) return -1;
            return r2.getExecutedAt().compareTo(r1.getExecutedAt());
        });
        
        // Apply pagination
        int start = page * size;
        int end = Math.min(start + size, allReports.size());
        
        if (start >= allReports.size()) {
            return Collections.emptyList();
        }
        
        return allReports.subList(start, end);
    }
    
    public ChartData getDailyTestsChart(int days) {
        List<TestReport> reports = getAllReports();
        LocalDate today = LocalDate.now();
        
        Map<LocalDate, Integer> testsByDate = new TreeMap<>();
        
        // Initialize last N days with 0
        for (int i = days - 1; i >= 0; i--) {
            testsByDate.put(today.minusDays(i), 0);
        }
        
        // Count tests by date
        for (TestReport report : reports) {
            if (report.getExecutedAt() != null) {
                LocalDate date = report.getExecutedAt().toLocalDate();
                if (!date.isBefore(today.minusDays(days))) {
                    testsByDate.put(date, testsByDate.getOrDefault(date, 0) + 1);
                }
            }
        }
        
        ChartData chartData = new ChartData();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd");
        
        for (Map.Entry<LocalDate, Integer> entry : testsByDate.entrySet()) {
            chartData.getLabels().add(entry.getKey().format(formatter));
            chartData.getData().add(entry.getValue());
        }
        
        return chartData;
    }
    
    public ChartData getIssuesChart() {
        DashboardStats stats = getStats();
        Map<String, Integer> issuesBySeverity = stats.getIssuesBySeverity();
        
        ChartData chartData = new ChartData();
        chartData.getLabels().addAll(Arrays.asList("High", "Medium", "Low"));
        chartData.getData().addAll(Arrays.asList(
            issuesBySeverity.getOrDefault("HIGH", 0),
            issuesBySeverity.getOrDefault("MEDIUM", 0),
            issuesBySeverity.getOrDefault("LOW", 0)
        ));
        
        return chartData;
    }
    
    private List<TestReport> getAllReports() {
        Path reportDir = Paths.get(REPORT_DIR);
        
        if (!Files.exists(reportDir)) {
            log.warn("Report directory does not exist: {}", REPORT_DIR);
            return Collections.emptyList();
        }
        
        try (Stream<Path> paths = Files.list(reportDir)) {
            return paths
                .filter(path -> path.toString().endsWith(".md"))
                .map(reportParserService::parseReport)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to read reports from directory: {}", REPORT_DIR, e);
            return Collections.emptyList();
        }
    }
}
