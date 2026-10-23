package org.ulpgc.tarantino.common.operations;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/** The name of this replica, its container hostname: the transition lines and a batch's {@code owner} carry it. */
public final class Replica {

    private static final String UNKNOWN = "-";

    private Replica() {
    }

    public static String name() {
        return Optional.ofNullable(System.getenv("HOSTNAME")).orElseGet(Replica::localHostName);
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return UNKNOWN;
        }
    }
}
