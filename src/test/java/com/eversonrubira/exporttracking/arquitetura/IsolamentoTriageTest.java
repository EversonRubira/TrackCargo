package com.eversonrubira.exporttracking.arquitetura;

import com.eversonrubira.exporttracking.ExportTrackingApplication;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// AI Test Triage: componente de CI isolado do produto (aprovado em 06/11/2026).
// 1) o triage nao usa conta, pedido, config nem a classe da aplicacao;
// 2) o produto nao usa o triage;
// 3) o triage e Java simples: sem Spring nem JPA, para correr no CI sem contexto nem base de dados.
// Mesmo criterio do FronteiraModulosTest: so codigo de producao.
@AnalyzeClasses(packages = "com.eversonrubira.exporttracking", importOptions = ImportOption.DoNotIncludeTests.class)
class IsolamentoTriageTest {

    private static final String BASE = "com.eversonrubira.exporttracking.";

    @ArchTest
    static final ArchRule triageNaoDependeDoProduto = noClasses()
            .that().resideInAPackage(BASE + "triage..")
            .should().dependOnClassesThat(
                    resideInAnyPackage(BASE + "conta..", BASE + "pedido..", BASE + "config..")
                            .or(equivalentTo(ExportTrackingApplication.class)));

    @ArchTest
    static final ArchRule produtoNaoDependeDoTriage = noClasses()
            .that(resideInAnyPackage(BASE + "conta..", BASE + "pedido..", BASE + "config..")
                    .or(equivalentTo(ExportTrackingApplication.class)))
            .should().dependOnClassesThat().resideInAPackage(BASE + "triage..");

    @ArchTest
    static final ArchRule triageNaoUsaSpringNemJpa = noClasses()
            .that().resideInAPackage(BASE + "triage..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..");
}
