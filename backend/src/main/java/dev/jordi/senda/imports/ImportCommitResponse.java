package dev.jordi.senda.imports;

/**
 * Outcome of a batch import: how many transactions were created and how many
 * were skipped as duplicates of existing ones.
 */
public record ImportCommitResponse(int imported, int skipped) {
}
