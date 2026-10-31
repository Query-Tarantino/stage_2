package org.ulpgc.tarantino.bench.manifest;

import java.util.Map;

/** One trial of a run with trials (SPEC §9): its name and how each service ran once its stack was up. */
public record Trial(String name, Map<String, ServiceConfiguration> services) {
}
