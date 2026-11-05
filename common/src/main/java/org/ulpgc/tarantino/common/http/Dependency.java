package org.ulpgc.tarantino.common.http;

import java.util.function.BooleanSupplier;

/** Something a service needs, named as {@code /health} lists it in {@code down} when it does not answer (SPEC §6.5). */
public record Dependency(String name, BooleanSupplier answers) {
}
