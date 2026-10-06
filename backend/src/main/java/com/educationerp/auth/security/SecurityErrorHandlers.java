package com.educationerp.auth.security;

import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.common.error.ErrorResponse;
import com.educationerp.common.web.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        write(response, request, ErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        boolean authenticated = SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated();
        write(response, request, authenticated ? ErrorCode.ACCESS_DENIED : ErrorCode.AUTHENTICATION_REQUIRED);
    }

    private void write(HttpServletResponse response, HttpServletRequest request, ErrorCode code) throws IOException {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                code.httpStatus(),
                code.name(),
                code.defaultMessage(),
                null,
                request.getRequestURI(),
                RequestContext.getRequestId());
        response.setStatus(code.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    public static AppException deny() {
        return AppException.denied(ErrorCode.ACCESS_DENIED.defaultMessage());
    }

    public static Map<String, Object> emptyMetadata() {
        return Map.of();
    }
}
