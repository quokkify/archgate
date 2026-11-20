package dev.quokkify.archgate.cases.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import dev.quokkify.archgate.api.Environment;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.core.GateRunServices;
import dev.quokkify.archgate.core.VariableEnvironment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Context over imported fixture classes as the test view, with the engine's own environment and shared values,
 * counting how often the test view is read and recording every shared name asked for.
 */
final class FixtureContext implements ArchUnitContext {

    private final JavaClasses testClasses;
    private final VariableEnvironment environment;
    private final GateRunServices services;
    private final AtomicInteger testCalls = new AtomicInteger();
    private final List<String> sharedNames = new CopyOnWriteArrayList<>();

    FixtureContext(JavaClasses testClasses, Map<String, String> variables) {
        this(testClasses, variables, false);
    }

    FixtureContext(JavaClasses testClasses, Map<String, String> variables, boolean offline) {
        this.testClasses = testClasses;
        Map<String, String> copy = new HashMap<>(variables);
        this.environment = new VariableEnvironment(copy::get, offline);
        this.services = new GateRunServices(environment);
    }

    int testCalls() {
        return testCalls.get();
    }

    List<String> sharedNames() {
        return List.copyOf(sharedNames);
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
        testCalls.incrementAndGet();
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
        sharedNames.add(name);
        return services.shared(name, build);
    }
}
