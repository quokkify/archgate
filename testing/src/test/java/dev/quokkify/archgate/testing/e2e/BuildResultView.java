package dev.quokkify.archgate.testing.e2e;

import java.nio.file.Path;

record BuildResultView(boolean success, String log, String report, Path reportPath) {}
