package com.jereplatform.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.jereplatform");

    @Test
    void modulesRespectAllowedDependencyDirections() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Kernel").definedBy("com.jereplatform.kernel..")
                .layer("Commercial").definedBy("com.jereplatform.commercial..")
                .layer("Academy").definedBy("com.jereplatform.academy..")
                .layer("Application").definedBy("com.jereplatform.app..")
                .whereLayer("Kernel").mayOnlyBeAccessedByLayers("Commercial", "Academy", "Application")
                .whereLayer("Commercial").mayOnlyBeAccessedByLayers("Academy", "Application")
                .whereLayer("Academy").mayOnlyBeAccessedByLayers("Application")
                .whereLayer("Application").mayNotBeAccessedByAnyLayer()
                .check(classes);
    }

    @Test
    void topLevelModulesRemainFreeOfCycles() {
        slices()
                .matching("com.jereplatform.(*)..")
                .should()
                .beFreeOfCycles()
                .check(classes);
    }
}
