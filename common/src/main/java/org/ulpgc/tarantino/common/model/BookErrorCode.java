package org.ulpgc.tarantino.common.model;

public enum BookErrorCode {
    NOT_FOUND,
    MISSING_MARKERS,
    NETWORK_ERROR,
    STORAGE_ERROR,
    LEASE_EXPIRED,
    NOT_IN_DATALAKE
}
