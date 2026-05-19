package com.ec.api.controller;

import com.ec.api.dto.DashboardResponse;
import com.ec.api.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardResponse getDashboard() {
        return dashboardService.getDashboard();
    }
}
