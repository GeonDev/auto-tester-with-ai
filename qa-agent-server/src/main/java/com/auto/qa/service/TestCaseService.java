package com.auto.qa.service;

import com.auto.qa.dto.TestCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class TestCaseService {
    
    private static final String TEST_CASES_DIR = "qa-prompts/test-cases";
    private final ObjectMapper objectMapper;
    
    public TestCaseService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        // Create test-cases directory if not exists
        try {
            Path dir = Paths.get(TEST_CASES_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("Created test-cases directory: {}", TEST_CASES_DIR);
            }
        } catch (IOException e) {
            log.error("Failed to create test-cases directory", e);
        }
    }
    
    public List<TestCase> getAllTestCases() {
        Path testCasesDir = Paths.get(TEST_CASES_DIR);
        
        if (!Files.exists(testCasesDir)) {
            return Collections.emptyList();
        }
        
        try (Stream<Path> paths = Files.list(testCasesDir)) {
            return paths
                .filter(path -> path.toString().endsWith(".json"))
                .map(this::readTestCase)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TestCase::getCreatedAt).reversed())
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to read test cases", e);
            return Collections.emptyList();
        }
    }
    
    public Optional<TestCase> getTestCase(String id) {
        Path filePath = Paths.get(TEST_CASES_DIR, id + ".json");
        
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        
        return Optional.ofNullable(readTestCase(filePath));
    }
    
    public TestCase createTestCase(TestCase testCase) {
        if (testCase.getId() == null || testCase.getId().isEmpty()) {
            testCase.setId(UUID.randomUUID().toString());
        }
        
        testCase.setCreatedAt(LocalDateTime.now());
        testCase.setUpdatedAt(LocalDateTime.now());
        testCase.setExecutionCount(0);
        
        saveTestCase(testCase);
        return testCase;
    }
    
    public TestCase updateTestCase(String id, TestCase testCase) {
        Optional<TestCase> existing = getTestCase(id);
        
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Test case not found: " + id);
        }
        
        TestCase updated = existing.get();
        updated.setName(testCase.getName());
        updated.setUrl(testCase.getUrl());
        updated.setPrompt(testCase.getPrompt());
        updated.setTags(testCase.getTags());
        updated.setUpdatedAt(LocalDateTime.now());
        
        saveTestCase(updated);
        return updated;
    }
    
    public void deleteTestCase(String id) {
        Path filePath = Paths.get(TEST_CASES_DIR, id + ".json");
        
        try {
            Files.deleteIfExists(filePath);
            log.info("Deleted test case: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete test case: {}", id, e);
            throw new RuntimeException("Failed to delete test case", e);
        }
    }
    
    public void incrementExecutionCount(String id) {
        Optional<TestCase> testCase = getTestCase(id);
        
        if (testCase.isPresent()) {
            TestCase tc = testCase.get();
            tc.setExecutionCount(tc.getExecutionCount() + 1);
            tc.setLastExecutedAt(LocalDateTime.now());
            saveTestCase(tc);
        }
    }
    
    private TestCase readTestCase(Path filePath) {
        try {
            return objectMapper.readValue(filePath.toFile(), TestCase.class);
        } catch (IOException e) {
            log.error("Failed to read test case: {}", filePath, e);
            return null;
        }
    }
    
    private void saveTestCase(TestCase testCase) {
        Path filePath = Paths.get(TEST_CASES_DIR, testCase.getId() + ".json");
        
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(filePath.toFile(), testCase);
            log.info("Saved test case: {}", testCase.getId());
        } catch (IOException e) {
            log.error("Failed to save test case: {}", testCase.getId(), e);
            throw new RuntimeException("Failed to save test case", e);
        }
    }
}
