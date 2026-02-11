package com.auto.qa.controller;

import com.auto.qa.dto.TestCase;
import com.auto.qa.service.TestCaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TestCaseController {
    
    private final TestCaseService testCaseService;
    
    @GetMapping("/test-cases")
    public String testCasesPage(Model model) {
        model.addAttribute("pageTitle", "Test Cases");
        return "test-cases";
    }
    
    @GetMapping("/api/test-cases")
    @ResponseBody
    public ResponseEntity<List<TestCase>> getAllTestCases() {
        List<TestCase> testCases = testCaseService.getAllTestCases();
        return ResponseEntity.ok(testCases);
    }
    
    @GetMapping("/api/test-cases/{id}")
    @ResponseBody
    public ResponseEntity<TestCase> getTestCase(@PathVariable String id) {
        return testCaseService.getTestCase(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/api/test-cases")
    @ResponseBody
    public ResponseEntity<TestCase> createTestCase(@RequestBody TestCase testCase) {
        TestCase created = testCaseService.createTestCase(testCase);
        return ResponseEntity.ok(created);
    }
    
    @PutMapping("/api/test-cases/{id}")
    @ResponseBody
    public ResponseEntity<TestCase> updateTestCase(
            @PathVariable String id,
            @RequestBody TestCase testCase) {
        try {
            TestCase updated = testCaseService.updateTestCase(id, testCase);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/api/test-cases/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteTestCase(@PathVariable String id) {
        testCaseService.deleteTestCase(id);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/api/test-cases/{id}/run")
    @ResponseBody
    public ResponseEntity<Map<String, String>> runTestCase(@PathVariable String id) {
        return testCaseService.getTestCase(id)
            .map(testCase -> {
                testCaseService.incrementExecutionCount(id);
                
                // Note: Test case execution should be done via WebSocket in chat interface
                // This endpoint just marks the test case as executed
                
                return ResponseEntity.ok(Map.of(
                    "status", "queued",
                    "message", "Test case queued. Please use the chat interface to see results.",
                    "testCaseId", id,
                    "url", testCase.getUrl(),
                    "prompt", testCase.getPrompt()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
