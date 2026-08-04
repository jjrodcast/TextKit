// Mocha's default 2s per-test cap is too tight for the deterministic stress smokes
// (EditingStressTest, UndoRedoStressTest) on a cold CI runner — the suite completes but the
// slowest smoke crosses 2s and is reported as a bare "Error". The full sweeps stay JVM-only;
// this only gives the browser smokes room to finish.
config.set({
  client: {
    mocha: {
      timeout: 10000,
    },
  },
});
