package com.sanaiddalgi.hub;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.sanaiddalgi.hub")
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule servicesDoNotDependOnWeb =
            noClasses()
                    .that().resideInAnyPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("com.sanaiddalgi.hub..web..");

    @ArchTest
    static final ArchRule reposDoNotDependOnServices =
            noClasses()
                    .that().resideInAnyPackage("..repo..")
                    .should().dependOnClassesThat().resideInAnyPackage("..service..");

    @ArchTest
    static final ArchRule reposDoNotDependOnWeb =
            noClasses()
                    .that().resideInAnyPackage("..repo..")
                    .should().dependOnClassesThat().resideInAPackage("com.sanaiddalgi.hub..web..");
}
