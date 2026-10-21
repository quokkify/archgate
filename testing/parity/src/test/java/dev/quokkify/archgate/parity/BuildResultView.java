package dev.quokkify.archgate.parity;

import java.nio.file.Path;

record BuildResultView(boolean success, String log, String report, Path reportPath) {}
