package com.jereplatform.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jereplatform.kernel.authorization.api.PlatformPermission;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void backendPermissionEnumMatchesCanonicalContract() throws Exception {
        var contract = objectMapper.readValue(
            Files.readString(findRepositoryRoot().resolve(
                "contracts/authorization/permissions.json")),
            new TypeReference<List<PermissionContractRow>>() { }
        );

        var backend = java.util.Arrays.stream(PlatformPermission.values())
            .map(permission -> new PermissionContractRow(
                permission.code(),
                permission.moduleCode(),
                permission.branchScoped()
            ))
            .sorted(java.util.Comparator.comparing(PermissionContractRow::code))
            .toList();
        var canonical = contract.stream()
            .sorted(java.util.Comparator.comparing(PermissionContractRow::code))
            .toList();

        assertThat(backend).containsExactlyElementsOf(canonical);
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("contracts/authorization/permissions.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }

    private record PermissionContractRow(
        String code,
        String moduleCode,
        boolean branchScoped
    ) {
    }
}
