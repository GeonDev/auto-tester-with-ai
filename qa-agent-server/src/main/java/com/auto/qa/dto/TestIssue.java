package com.auto.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestIssue {
    private String severity;    // HIGH, MEDIUM, LOW
    private String category;    // UI/UX, ACCESSIBILITY, FUNCTIONAL
    private String description;
    private String suggestion;
}
