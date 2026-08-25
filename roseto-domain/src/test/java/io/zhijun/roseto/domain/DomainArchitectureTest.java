package io.zhijun.roseto.domain;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.zhijun.roseto.domain")
class DomainArchitectureTest {
    @ArchTest
    static final ArchRule domainMustNotDependOnFrameworks = noClasses()
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "com.baomidou.mybatisplus..",
            "org.postgresql..",
            "org.redisson..",
            "org.springframework.security..",
            "jakarta.servlet.."
        );
}
