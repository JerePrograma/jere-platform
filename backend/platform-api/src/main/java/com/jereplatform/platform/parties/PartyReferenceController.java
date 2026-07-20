package com.jereplatform.platform.parties;

import com.jereplatform.commercial.parties.api.PartyId;
import com.jereplatform.commercial.parties.api.PartyLifecycleStatus;
import com.jereplatform.commercial.parties.api.PartyReconciliationFinding;
import com.jereplatform.commercial.parties.api.PartyReconciliationReport;
import com.jereplatform.commercial.parties.api.PartyReferenceView;
import com.jereplatform.commercial.parties.api.PartySourceCandidate;
import com.jereplatform.commercial.parties.api.PartySourceRecord;
import com.jereplatform.commercial.parties.application.PartyReconciliationService;
import com.jereplatform.commercial.parties.application.PartyReferenceService;
import com.jereplatform.platform.identity.PlatformPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/party-references")
public class PartyReferenceController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final PartyReferenceService partyReferenceService;
    private final PartyReconciliationService reconciliationService;

    public PartyReferenceController(
        PartyReferenceService partyReferenceService,
        PartyReconciliationService reconciliationService
    ) {
        this.partyReferenceService = partyReferenceService;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    @PreAuthorize("@platformAuthorization.can(authentication, 'commercial.parties.read')")
    public PartySearchResponse search(
        Authentication authentication,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "false") boolean includeInactive,
        @RequestParam(defaultValue = "25") int limit
    ) {
        var parties = partyReferenceService.search(
            principal(authentication).tenantContext(),
            q,
            includeInactive,
            limit
        ).stream().map(PartyReferenceController::response).toList();
        return new PartySearchResponse(parties, parties.size());
    }

    @GetMapping("/{partyId}")
    @PreAuthorize("@platformAuthorization.can(authentication, 'commercial.parties.read')")
    public PartyReferenceResponse find(
        Authentication authentication,
        @PathVariable UUID partyId
    ) {
        return response(partyReferenceService.find(
            principal(authentication).tenantContext(),
            new PartyId(partyId)
        ));
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@platformAuthorization.can(authentication, 'commercial.parties.manage')")
    public PartyImportResponse importParty(
        Authentication authentication,
        @RequestHeader(IDEMPOTENCY_HEADER) @NotBlank @Size(max = 500) String idempotencyKey,
        @Valid @RequestBody PartyImportRequest request
    ) {
        var result = partyReferenceService.importRecord(
            principal(authentication).tenantContext(),
            new PartySourceRecord(
                request.sourceType(),
                request.sourceId(),
                request.displayName(),
                request.active() ? PartyLifecycleStatus.ACTIVE : PartyLifecycleStatus.INACTIVE
            ),
            idempotencyKey
        );
        return new PartyImportResponse(
            response(result.value().party()),
            result.value().mutation().name(),
            result.replayed()
        );
    }

    @PostMapping("/reconciliation")
    @PreAuthorize("@platformAuthorization.can(authentication, 'commercial.parties.manage')")
    public PartyReconciliationResponse reconcile(
        Authentication authentication,
        @Valid @RequestBody PartyReconciliationRequest request
    ) {
        var report = reconciliationService.analyze(
            principal(authentication).tenantContext(),
            request.candidates().stream().map(candidate -> new PartySourceCandidate(
                candidate.sourceType(),
                candidate.sourceId(),
                candidate.displayName(),
                candidate.active()
            )).toList()
        );
        return new PartyReconciliationResponse(
            report.totalCandidates(),
            report.validCandidates(),
            report.newMappings(),
            report.unchangedMappings(),
            report.changedNames(),
            report.statusChanges(),
            report.absentMappings(),
            report.hasBlockingFindings(),
            report.findings()
        );
    }

    private static PlatformPrincipal principal(Authentication authentication) {
        return (PlatformPrincipal) authentication.getPrincipal();
    }

    private static PartyReferenceResponse response(PartyReferenceView party) {
        return new PartyReferenceResponse(
            party.id().value(),
            party.sourceType(),
            party.sourceId(),
            party.currentDisplayName(),
            party.active(),
            party.createdAt(),
            party.updatedAt()
        );
    }

    public record PartyImportRequest(
        @NotBlank @Size(max = 40) String sourceType,
        @NotBlank @Size(max = 160) String sourceId,
        @NotBlank @Size(max = 200) String displayName,
        boolean active
    ) {
    }

    public record PartyImportResponse(
        PartyReferenceResponse party,
        String mutation,
        boolean replayed
    ) {
    }

    public record PartyReferenceResponse(
        UUID id,
        String sourceType,
        String sourceId,
        String displayName,
        boolean active,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record PartySearchResponse(
        List<PartyReferenceResponse> parties,
        int count
    ) {
    }

    public record PartyReconciliationCandidateRequest(
        String sourceType,
        String sourceId,
        String displayName,
        Boolean active
    ) {
    }

    public record PartyReconciliationRequest(
        @NotEmpty @Size(max = 10_000) List<@NotNull PartyReconciliationCandidateRequest> candidates
    ) {
    }

    public record PartyReconciliationResponse(
        int totalCandidates,
        int validCandidates,
        int newMappings,
        int unchangedMappings,
        int changedNames,
        int statusChanges,
        int absentMappings,
        boolean blockingFindings,
        List<PartyReconciliationFinding> findings
    ) {
    }
}
