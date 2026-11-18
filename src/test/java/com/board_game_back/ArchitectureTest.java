package com.board_game_back;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 계층 규칙 (CLAUDE.md "백엔드 규칙 › 계층"). 어기면 빌드가 실패한다.
 * Controller → Service → Repository 한 방향으로만 부른다.
 */
@AnalyzeClasses(packages = "com.board_game_back", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_do_not_use_repositories = noClasses()
            .that().resideInAPackage("..Controller..")
            .should().dependOnClassesThat().resideInAPackage("..Repository..")
            .because("Controller는 Service만 부른다. 조회 하나라도 Service에 메서드를 만든다");

    @ArchTest
    static final ArchRule services_do_not_use_controllers = noClasses()
            .that().resideInAPackage("..Service..")
            .should().dependOnClassesThat().resideInAPackage("..Controller..")
            .because("Service는 웹 계층을 모른다");

    @ArchTest
    static final ArchRule repositories_do_not_use_upper_layers = noClasses()
            .that().resideInAPackage("..Repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..Service..", "..Controller..")
            .because("Repository는 DB 접근만 한다");
}
