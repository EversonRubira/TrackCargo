package com.eversonrubira.exporttracking.arquitetura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

// ADR-002, regra 1: um modulo nao usa as entidades nem os repositorios de
// outro, fala com ele pelo service (ou por um id / constante simples, como
// EmpresaPadrao.ID). A mesma regra, aplicada nos dois sentidos.
//
// So o codigo de producao (DoNotIncludeTests): os testes montam dados de
// varios modulos e nao sao uma fronteira. Se esta regra gerar mais ruido
// do que protege, remove-se (decisao registada no ADR).
@AnalyzeClasses(packages = "com.eversonrubira.exporttracking", importOptions = ImportOption.DoNotIncludeTests.class)
class FronteiraModulosTest {

    private static final String BASE = "com.eversonrubira.exporttracking.";

    @ArchTest
    static final ArchRule pedidoNaoUsaEntidadesNemRepositoriosDeConta = naoUsaEntidadesNemRepositorios("pedido", "conta");

    @ArchTest
    static final ArchRule contaNaoUsaEntidadesNemRepositoriosDePedido = naoUsaEntidadesNemRepositorios("conta", "pedido");

    private static ArchRule naoUsaEntidadesNemRepositorios(String origem, String destino) {
        return noClasses().that().resideInAPackage(BASE + origem + "..")
                .should().dependOnClassesThat(entidadeOuRepositorioDe(destino));
    }

    // Entidade = anotada com @Entity; repositorio = qualquer interface do
    // Spring Data (JpaRepository herda de Repository).
    private static DescribedPredicate<JavaClass> entidadeOuRepositorioDe(String modulo) {
        String pacote = BASE + modulo;
        return DescribedPredicate.describe("sao entidades ou repositorios do modulo " + modulo,
                classe -> (classe.getPackageName().equals(pacote) || classe.getPackageName().startsWith(pacote + "."))
                        && (classe.isAnnotatedWith(Entity.class) || classe.isAssignableTo(Repository.class)));
    }
}
