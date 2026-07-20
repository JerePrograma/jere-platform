package com.jereplatform.platform.parties;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PartySourceExportController.class)
class PartySourceExportExceptionHandler {

    @ExceptionHandler(PartySourceExportException.class)
    ResponseEntity<Map<String, String>> sourceExportFailure(PartySourceExportException failure) {
        return switch (failure.reason()) {
            case AUTHENTICATION -> response(
                HttpStatus.UNAUTHORIZED, "party_source_authentication_failed");
            case CONFIGURATION -> response(
                HttpStatus.SERVICE_UNAVAILABLE, "party_source_not_configured");
            case INVALID_ARTIFACT -> response(
                HttpStatus.BAD_REQUEST, "invalid_party_source_export");
            case TENANT_MISMATCH -> response(
                HttpStatus.FORBIDDEN, "party_source_tenant_mismatch");
        };
    }

    private static ResponseEntity<Map<String, String>> response(
        HttpStatus status,
        String code
    ) {
        return ResponseEntity.status(status).body(Map.of("code", code));
    }
}
