package com.auto.qa.controller;

import com.auto.qa.dto.ChartData;
import com.auto.qa.dto.DashboardStats;
import com.auto.qa.dto.TestReport;
import com.auto.qa.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardController {
    
    private final DashboardService dashboardService;
    
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        return "dashboard";
    }
    
    @GetMapping("/api/dashboard/stats")
    @ResponseBody
    public ResponseEntity<DashboardStats> getStats() {
        DashboardStats stats = dashboardService.getStats();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/api/dashboard/reports")
    @ResponseBody
    public ResponseEntity<List<TestReport>> getReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<TestReport> reports = dashboardService.getReports(page, size);
        return ResponseEntity.ok(reports);
    }
    
    @GetMapping("/api/dashboard/charts/daily")
    @ResponseBody
    public ResponseEntity<ChartData> getDailyChart(
            @RequestParam(defaultValue = "7") int days) {
        ChartData chartData = dashboardService.getDailyTestsChart(days);
        return ResponseEntity.ok(chartData);
    }
    
    @GetMapping("/api/dashboard/charts/issues")
    @ResponseBody
    public ResponseEntity<ChartData> getIssuesChart() {
        ChartData chartData = dashboardService.getIssuesChart();
        return ResponseEntity.ok(chartData);
    }
}
