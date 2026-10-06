package com.educationerp.dashboard.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.dashboard.DashboardDtos;
import com.educationerp.dashboard.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The staff dashboards.
 *
 * <p>Two screens, for the two jobs: running the school and running its books. Both are figures
 * rather than charts, because a chart of a single number is a decoration.
 */
@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private final DashboardService dashboards;

    public DashboardController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/principal")
    public ApiResponse<DashboardDtos.PrincipalDashboard> principal() {
        return ApiResponse.ok(dashboards.principal());
    }

    @GetMapping("/accountant")
    public ApiResponse<DashboardDtos.AccountantDashboard> accountant() {
        return ApiResponse.ok(dashboards.accountant());
    }
}
