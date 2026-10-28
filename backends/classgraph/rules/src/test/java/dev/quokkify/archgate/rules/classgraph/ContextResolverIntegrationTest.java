package dev.quokkify.archgate.rules.classgraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.context.classgraph.ClassGraphContext;
import dev.quokkify.archgate.core.ContextResolver;
import dev.quokkify.archgate.core.GateResult;
import dev.quokkify.archgate.core.GateRunner;
import dev.quokkify.archgate.core.SimpleProjectLayout;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextResolverIntegrationTest {

    @Test
    void resolverClosesTheContextAfterTheRun() throws URISyntaxException {
        Path classes = Path.of(ContextResolverIntegrationTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Path services = Path.of(ContextResolverIntegrationTest.class
                .getResource("/classgraph-cases/listed")
                .toURI());
        SimpleProjectLayout layout = new SimpleProjectLayout(
                "archfixtures.classgraph",
                List.of(classes, services),
                List.of(),
                List.of(),
                List.of(),
                ContextResolverIntegrationTest.class.getClassLoader());
        ContextResolver resolver = new ContextResolver(layout, ContextResolverIntegrationTest.class.getClassLoader());
        List<ArchitectureRule<?>> rules = List.of(new ServiceFileConsistencyRule());

        GateResult result = GateRunner.run(rules, resolver);
        ClassGraphContext context = resolver.get(ClassGraphContext.class);
        resolver.close();

        assertThat(result.outcomes()).hasSize(1);
        assertThat(result.outcomes().get(0).finding()).isNull();
        assertThat(resolver.buildTimes()).containsKey("classgraph main");
        assertThatThrownBy(() -> context.scan(Scope.MAIN)).isInstanceOf(IllegalStateException.class);
    }
}
