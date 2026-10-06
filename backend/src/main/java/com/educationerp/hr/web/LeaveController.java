package com.educationerp.hr.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.hr.HrDtos;
import com.educationerp.hr.LeaveRequest;
import com.educationerp.hr.LeaveService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/leave")
@Tag(name = "HR - Leave")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService service;

    @GetMapping("/types")
    public ApiResponse<List<HrDtos.LeaveTypeResponse>> types() {
        return ApiResponse.ok(service.listTypes());
    }

    @PostMapping("/types")
    public ApiResponse<HrDtos.LeaveTypeResponse> createType(@Valid @RequestBody HrDtos.CreateLeaveType request) {
        return ApiResponse.ok(service.createType(request));
    }

    @GetMapping("/balances")
    public ApiResponse<List<HrDtos.LeaveBalanceResponse>> balances(
            @RequestParam UUID employeeId,
            @RequestParam(defaultValue = "0") int year) {
        int resolved = year == 0 ? java.time.Year.now().getValue() : year;
        return ApiResponse.ok(service.balances(employeeId, resolved));
    }

    @PostMapping("/balances/open")
    public ApiResponse<List<HrDtos.LeaveBalanceResponse>> openEntitlements(
            @RequestParam UUID employeeId,
            @RequestParam(defaultValue = "0") int year) {
        int resolved = year == 0 ? java.time.Year.now().getValue() : year;
        return ApiResponse.ok(service.openEntitlements(employeeId, resolved));
    }

    @GetMapping("/requests")
    public ApiResponse<List<HrDtos.LeaveRequestResponse>> requests(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) LeaveRequest.Status status) {
        return ApiResponse.ok(service.listRequests(employeeId, status));
    }

    @GetMapping("/requests/{id}")
    public ApiResponse<HrDtos.LeaveRequestResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/requests")
    public ApiResponse<HrDtos.LeaveRequestResponse> apply(@Valid @RequestBody HrDtos.ApplyLeave request) {
        return ApiResponse.ok(service.apply(request));
    }

    @PutMapping("/requests/{id}/decision")
    public ApiResponse<HrDtos.LeaveRequestResponse> decide(@PathVariable UUID id,
                                                            @Valid @RequestBody HrDtos.DecideLeave request) {
        return ApiResponse.ok(service.decide(id, request));
    }

    @PostMapping("/requests/{id}/cancel")
    public ApiResponse<HrDtos.LeaveRequestResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(service.cancel(id));
    }
}