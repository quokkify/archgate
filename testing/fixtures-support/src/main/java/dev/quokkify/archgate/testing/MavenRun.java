package dev.quokkify.archgate.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;

public final class MavenRun {

    private MavenRun() {}

    public record Outcome(boolean succeeded, String log) {}

    public static Outcome execute(Path project, Path localRepo, MavenCache cache, Expectation expectation)
            throws VerificationException, IOException {
        Verifier verifier = ForkedMaven.pinJavaHome(new Verifier(project.toString()));
        verifier.setLocalRepo(localRepo.toString());
        verifier.setLogFileName("log-run-" + expectation.run() + ".txt");
        List<String> arguments =
                new ArrayList<>(List.of("-s", "settings.xml", "-Pit-repo", cache.tailArgument(), "verify"));
        arguments.addAll(expectation.args(BuildTool.MAVEN));
        verifier.addCliArguments(arguments.toArray(String[]::new));
        boolean succeeded = true;
        try {
            verifier.execute();
        } catch (VerificationException e) {
            succeeded = false;
        }
        return new Outcome(succeeded, Files.readString(project.resolve(verifier.getLogFileName())));
    }
}
