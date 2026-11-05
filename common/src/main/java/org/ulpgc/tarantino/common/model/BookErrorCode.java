package org.ulpgc.tarantino.common.model;

/** Why a download or an indexing failed, the {@code error_code} of a book (SPEC §4.5). */
public enum BookErrorCode {
    NOT_FOUND,
    MISSING_MARKERS,
    NETWORK_ERROR,
    STORAGE_ERROR,
    LEASE_EXPIRED,
    NOT_IN_DATALAKE
}
