package com.educationerp.communication.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import com.educationerp.communication.CommunicationDtos;
import com.educationerp.communication.CommunicationService;
import com.educationerp.communication.Notice;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

/**
 * Notices and their templates. The inbox itself lives on the portal controller, because it is
 * read by the person receiving the messages rather than by the institution sending them.
 */
@RestController
@RequestMapping("/api/v1/communication")
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationService service;

    // --------------------------------------------------------------- templates

    @GetMapping("/templates")
    public ApiResponse<List<CommunicationDtos.TemplateResponse>> templates() {
        return ApiResponse.ok(service.listTemplates());
    }

    @PostMapping("/templates")
    public ApiResponse<CommunicationDtos.TemplateResponse> createTemplate(
            @Valid @RequestBody CommunicationDtos.TemplateRequest request) {
        return ApiResponse.ok(service.createTemplate(request));
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<CommunicationDtos.TemplateResponse> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody CommunicationDtos.TemplateRequest request) {
        return ApiResponse.ok(service.updateTemplate(id, request));
    }

    // ----------------------------------------------------------------- notices

    @GetMapping("/notices")
    public ApiResponse<PageResponse<CommunicationDtos.NoticeResponse>> notices(
            @RequestParam(required = false) Notice.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listNotices(status,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/notices")
    public ApiResponse<CommunicationDtos.NoticeResponse> createNotice(
            @Valid @RequestBody CommunicationDtos.NoticeRequest request) {
        return ApiResponse.ok(service.createNotice(request));
    }

    @GetMapping("/notices/{id}")
    public ApiResponse<CommunicationDtos.NoticeResponse> notice(@PathVariable UUID id) {
        return ApiResponse.ok(service.getNotice(id));
    }

    @PostMapping("/notices/{id}/publish")
    public ApiResponse<CommunicationDtos.DispatchSummary> publish(@PathVariable UUID id) {
        return ApiResponse.ok(service.publishNotice(id));
    }
}