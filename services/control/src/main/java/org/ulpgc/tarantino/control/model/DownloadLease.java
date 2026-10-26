package org.ulpgc.tarantino.control.model;

/** A {@code DOWNLOADING} book with the {@code claim_id} that “Release an expired download” filters by (SPEC §5.1). */
public record DownloadLease(int bookId, String claimId) {
}
