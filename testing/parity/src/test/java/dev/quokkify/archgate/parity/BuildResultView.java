package dev.quokkify.archgate.parity;

record BuildResultView(boolean success, String log, String report, java.nio.file.Path reportPath) {}
