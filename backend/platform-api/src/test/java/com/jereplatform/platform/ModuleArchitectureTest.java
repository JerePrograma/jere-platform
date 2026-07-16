package com.jereplatform.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.jereplatform",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule kernel_must_not_depend_on_business_modules =
        noClasses()
            .that().resideInAPackage("..kernel..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..commercial..", "..verticals..");

    @ArchTest
    static final ArchRule commercial_core_must_not_depend_on_verticals =
        noClasses()
            .that().resideInAPackage("..commercial..")
            .should().dependOnClassesThat()
            .resideInAPackage("..verticals..");

    @ArchTest
    static final ArchRule domain_modules_must_not_depend_on_platform_bootstrap =
        noClasses()
            .that().resideInAnyPackage("..kernel..", "..commercial..", "..verticals..")
            .should().dependOnClassesThat()
            .resideInAPackage("..platform..");

    @ArchTest
    static final ArchRule kernel_internal_packages_must_remain_encapsulated =
        noClasses()
            .that().resideInAnyPackage("..commercial..", "..verticals..", "..platform..")
            .should().dependOnClassesThat()
            .resideInAPackage("..kernel..internal..");

    @ArchTest
    static final ArchRule commercial_internal_packages_must_remain_encapsulated =
        noClasses()
            .that().resideInAnyPackage("..verticals..", "..platform..")
            .should().dependOnClassesThat()
            .resideInAPackage("..commercial..internal..");
}
