package com.auto.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {
    private String id;
    private String name;
    private String url;
    private String prompt;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private int executionCount = 0;
    private LocalDateTime lastExecutedAt;
}
