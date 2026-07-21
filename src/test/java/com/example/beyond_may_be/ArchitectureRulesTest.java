package com.example.beyond_may_be;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.example.beyond_may_be", importOptions = DoNotIncludeTests.class)
class ArchitectureRulesTest {

  @ArchTest
  static final ArchRule application_packages_should_follow_known_structure =
      classes()
          .should()
          .resideInAnyPackage(
              "com.example.beyond_may_be",
              "com.example.beyond_may_be.apiPayload..",
              "com.example.beyond_may_be.common..",
              "com.example.beyond_may_be.*.controller..",
              "com.example.beyond_may_be.*.converter..",
              "com.example.beyond_may_be.*.domain..",
              "com.example.beyond_may_be.*.dto..",
              "com.example.beyond_may_be.*.repository..",
              "com.example.beyond_may_be.*.service..");

  @ArchTest
  static final ArchRule domain_should_not_depend_on_application_or_web_layers =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .and()
          .resideOutsideOfPackage("..common.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..controller..",
              "..converter..",
              "..dto..",
              "..repository..",
              "..service..",
              "..apiPayload..",
              "..common.config..",
              "..common.logging..");

  @ArchTest
  static final ArchRule controllers_should_not_access_repositories_directly =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule services_should_not_depend_on_controllers =
      noClasses()
          .that()
          .resideInAPackage("..service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..controller..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule repositories_should_not_depend_on_application_layers =
      noClasses()
          .that()
          .resideInAPackage("..repository..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..service..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule converters_should_not_depend_on_application_layers =
      noClasses()
          .that()
          .resideInAPackage("..converter..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..repository..", "..service..")
          .allowEmptyShould(true);
}
