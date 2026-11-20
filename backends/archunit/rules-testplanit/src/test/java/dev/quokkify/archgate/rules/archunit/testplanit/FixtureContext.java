package dev.quokkify.archgate.rules.archunit.testplanit;

import com.tngtech.archunit.core.domain.JavaClasses;
import dev.quokkify.archgate.api.Environment;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.core.GateRunServices;
import dev.quokkify.archgate.core.VariableEnvironment;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Context over imported fixture classes as the test view, with the engine's own environment and shared values. */
final class FixtureContext implements ArchUnitContext {

    private final JavaClasses testClasses;
    private final VariableEnvironment environment;
    private final GateRunServices services;

    FixtureContext(JavaClasses testClasses, Map<String, String> variables) {
        this.testClasses = testClasses;
        Map<String, String> copy = new HashMap<>(variables);
        this.environment = new VariableEnvironment(copy::get, false);
        this.services = new GateRunServices(environment);
    }

    @Override
    public String rootPackage() {
        return "archfixtures";
    }

    @Override
    public JavaClasses main() {
        throw new UnsupportedOperationException("The fixture context has no main view");
    }

    @Override
    public JavaClasses test() {
        return testClasses;
    }

    @Override
    public JavaClasses all() {
        return testClasses;
    }

    @Override
    public ProjectLayout layout() {
        throw new UnsupportedOperationException("The fixture context has no project layout");
    }

    @Override
    public Environment environment() {
        return environment;
    }

    @Override
    public <T> T shared(String name, Supplier<T> build) {
        return services.shared(name, build);
    }
}
