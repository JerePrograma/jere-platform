package com.jereplatform.platform.parties;

import com.jereplatform.commercial.parties.api.PartyReconciliationFinding;
import com.jereplatform.commercial.parties.api.PartyReconciliationReport;
import com.jereplatform.platform.identity.PlatformPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/party-source-exports")
public class PartySourceExportController {

    private static final String SOURCE_HEADER = "X-Party-Source-Type";
    private static final String SIGNATURE_HEADER = "X-Party-Export-Signature";

    private final PartySourceExportService service;

    public PartySourceExportController(PartySourceExportService service) {
        this.service = service;
    }

    @PostMapping("/reconciliation")
    @PreAuthorize("@platformAuthorization.can(authentication, 'commercial.parties.manage')")
    public ReconciliationResponse reconcile(
        Authentication authentication,
        @RequestHeader(SOURCE_HEADER) @NotBlank @Size(max = 40) String sourceType,
        @RequestHeader(SIGNATURE_HEADER) @NotBlank @Size(max = 80) String signature,
        @RequestBody @Size(max = SignedPartySourceExportReader.MAX_BODY_BYTES) byte[] body
    ) {
        var analyzed = service.reconcile(
            principal(authentication).tenantContext(), sourceType, signature, body);
        return response(analyzed.export(), analyzed.report());
    }

    @PostMapping("/imports")
    @PreAuthorize("@platformAuthorization.can(authentication, 'commercial.parties.manage')")
    public ResponseEntity<ImportResponse> importExport(
        Authentication authentication,
        @RequestHeader(SOURCE_HEADER) @NotBlank @Size(max = 40) String sourceType,
        @RequestHeader(SIGNATURE_HEADER) @NotBlank @Size(max = 80) String signature,
        @RequestBody @Size(max = SignedPartySourceExportReader.MAX_BODY_BYTES) byte[] body
    ) {
        var outcome = service.importExport(
            principal(authentication).tenantContext(), sourceType, signature, body);
        var export = outcome.analyzed().export();
        var report = response(export, outcome.analyzed().report());
        if (outcome.result() == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new ImportResponse(report, false, false, 0, 0, 0, 0)
            );
        }
        var result = outcome.result();
        return ResponseEntity.ok(new ImportResponse(
            report,
            true,
            outcome.replayed(),
            result.totalRecords(),
            result.createdRecords(),
            result.updatedRecords(),
            result.unchangedRecords()
        ));
    }

    private static ReconciliationResponse response(
        PartySourceExport export,
        PartyReconciliationReport report
    ) {
        return new ReconciliationResponse(
            export.sourceType(),
            export.checkpoint(),
            export.nextCursor(),
            export.pageNumber(),
            export.pageCount(),
            export.fullSnapshot(),
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

    public record ReconciliationResponse(
        String sourceType,
        String checkpoint,
        String nextCursor,
        Integer pageNumber,
        Integer pageCount,
        boolean fullSnapshot,
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

    public record ImportResponse(
        ReconciliationResponse reconciliation,
        boolean accepted,
        boolean replayed,
        int totalRecords,
        int createdRecords,
        int updatedRecords,
        int unchangedRecords
    ) {
    }
}
