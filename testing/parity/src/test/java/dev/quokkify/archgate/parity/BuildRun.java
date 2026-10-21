package dev.quokkify.archgate.parity;

import dev.quokkify.archgate.testing.Expectation;

interface BuildRun {

    BuildResultView run(Expectation expectation);
}
