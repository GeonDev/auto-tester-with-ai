package com.auto.qa.service;

import com.auto.qa.dto.TestIssue;
import com.auto.qa.dto.TestReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ReportParserService {
    
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);
    private static final Pattern ISSUE_PATTERN = Pattern.compile("###\\s*🔴\\s*(High|HIGH).*?\\n-\\s*\\*\\*\\[(.*?)\\]\\*\\*:\\s*(.*?)\\n\\s*-\\s*제안:\\s*(.*?)(?=\\n###|\\n##|$)", Pattern.DOTALL);
    private static final Pattern MEDIUM_ISSUE_PATTERN = Pattern.compile("###\\s*🟡\\s*(Medium|MEDIUM).*?\\n-\\s*\\*\\*\\[(.*?)\\]\\*\\*:\\s*(.*?)\\n\\s*-\\s*제안:\\s*(.*?)(?=\\n###|\\n##|$)", Pattern.DOTALL);
    private static final Pattern LOW_ISSUE_PATTERN = Pattern.compile("###\\s*🟢\\s*(Low|LOW).*?\\n-\\s*\\*\\*\\[(.*?)\\]\\*\\*:\\s*(.*?)\\n\\s*-\\s*제안:\\s*(.*?)(?=\\n###|\\n##|$)", Pattern.DOTALL);
    
    public TestReport parseReport(Path reportPath) {
        try {
            String content = Files.readString(reportPath);
            String fileName = reportPath.getFileName().toString();
            
            TestReport.TestReportBuilder builder = TestReport.builder()
                .id(extractIdFromFileName(fileName))
                .filePath(reportPath.toString());
            
            // Parse frontmatter
            Matcher frontmatterMatcher = FRONTMATTER_PATTERN.matcher(content);
            if (frontmatterMatcher.find()) {
                String frontmatter = frontmatterMatcher.group(1);
                parseFrontmatter(frontmatter, builder);
                content = content.substring(frontmatterMatcher.end());
            }
            
            // Parse issues from markdown body
            List<TestIssue> issues = new ArrayList<>();
            issues.addAll(parseIssues(content, ISSUE_PATTERN, "HIGH"));
            issues.addAll(parseIssues(content, MEDIUM_ISSUE_PATTERN, "MEDIUM"));
            issues.addAll(parseIssues(content, LOW_ISSUE_PATTERN, "LOW"));
            builder.issues(issues);
            
            return builder.build();
            
        } catch (IOException e) {
            log.error("Failed to parse report: {}", reportPath, e);
            return null;
        }
    }
    
    private void parseFrontmatter(String frontmatter, TestReport.TestReportBuilder builder) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(frontmatter);
        
        if (data != null) {
            builder.url((String) data.get("url"));
            builder.model((String) data.get("model"));
            builder.status((String) data.getOrDefault("status", "SUCCESS"));
            
            if (data.containsKey("executedAt")) {
                String executedAtStr = (String) data.get("executedAt");
                builder.executedAt(LocalDateTime.parse(executedAtStr, DateTimeFormatter.ISO_DATE_TIME));
            }
            
            if (data.containsKey("executionTime")) {
                String timeStr = (String) data.get("executionTime");
                builder.executionTime(parseExecutionTime(timeStr));
            }
        }
    }
    
    private List<TestIssue> parseIssues(String content, Pattern pattern, String severity) {
        List<TestIssue> issues = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String category = matcher.group(2).trim();
            String description = matcher.group(3).trim();
            String suggestion = matcher.group(4).trim();
            
            issues.add(TestIssue.builder()
                .severity(severity)
                .category(category)
                .description(description)
                .suggestion(suggestion)
                .build());
        }
        
        return issues;
    }
    
    private String extractIdFromFileName(String fileName) {
        // Extract ID from filename like "report_20260207_143000.md"
        return fileName.replace(".md", "");
    }
    
    private Duration parseExecutionTime(String timeStr) {
        try {
            // Parse formats like "45s", "1m30s", "2m"
            if (timeStr.contains("m")) {
                String[] parts = timeStr.split("m");
                int minutes = Integer.parseInt(parts[0].trim());
                int seconds = 0;
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    seconds = Integer.parseInt(parts[1].replace("s", "").trim());
                }
                return Duration.ofMinutes(minutes).plusSeconds(seconds);
            } else if (timeStr.contains("s")) {
                int seconds = Integer.parseInt(timeStr.replace("s", "").trim());
                return Duration.ofSeconds(seconds);
            }
        } catch (Exception e) {
            log.warn("Failed to parse execution time: {}", timeStr);
        }
        return Duration.ZERO;
    }
}
