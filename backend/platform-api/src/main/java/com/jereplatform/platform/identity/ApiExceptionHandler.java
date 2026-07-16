package com.jereplatform.platform.identity;

import com.jereplatform.commercial.parties.api.PartyInactiveException;
import com.jereplatform.commercial.parties.api.PartyReferenceNotFoundException;
import com.jereplatform.kernel.authorization.api.AuthorizationDeniedException;
import com.jereplatform.kernel.identity.api.AuthenticationFailureException;
import com.jereplatform.kernel.reliability.api.IdempotencyConflictException;
import com.jereplatform.kernel.reliability.api.IdempotencyInProgressException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationFailureException.class)
    ResponseEntity<Map<String, String>> authenticationFailure() {
        return response(HttpStatus.UNAUTHORIZED, "authentication_failed");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<Map<String, String>> authorizationDenied() {
        return response(HttpStatus.FORBIDDEN, "authorization_denied");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<Map<String, String>> idempotencyConflict() {
        return response(HttpStatus.CONFLICT, "idempotency_key_conflict");
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    ResponseEntity<Map<String, String>> idempotencyInProgress() {
        return response(HttpStatus.CONFLICT, "idempotency_request_in_progress");
    }

    @ExceptionHandler(PartyReferenceNotFoundException.class)
    ResponseEntity<Map<String, String>> partyReferenceNotFound() {
        return response(HttpStatus.NOT_FOUND, "party_reference_not_found");
    }

    @ExceptionHandler(PartyInactiveException.class)
    ResponseEntity<Map<String, String>> partyInactive() {
        return response(HttpStatus.CONFLICT, "party_reference_inactive");
    }

    @ExceptionHandler(RefreshIntentRequiredException.class)
    ResponseEntity<Map<String, String>> refreshIntentRequired() {
        return response(HttpStatus.FORBIDDEN, "refresh_intent_required");
    }

    @ExceptionHandler(BootstrapAccessDeniedException.class)
    ResponseEntity<Map<String, String>> bootstrapAccessDenied() {
        return response(HttpStatus.FORBIDDEN, "bootstrap_access_denied");
    }

    @ExceptionHandler(BootstrapDisabledException.class)
    ResponseEntity<Map<String, String>> bootstrapDisabled() {
        return response(HttpStatus.NOT_FOUND, "not_found");
    }

    @ExceptionHandler(InvalidCorrelationIdException.class)
    ResponseEntity<Map<String, String>> invalidCorrelationId() {
        return response(HttpStatus.BAD_REQUEST, "invalid_correlation_id");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidRequest() {
        return response(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidArgument() {
        return response(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict() {
        return response(HttpStatus.CONFLICT, "operation_conflict");
    }

    private static ResponseEntity<Map<String, String>> response(
        HttpStatus status,
        String code
    ) {
        return ResponseEntity.status(status).body(Map.of("code", code));
    }
}
