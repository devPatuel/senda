package dev.jordi.senda.allocation;

import java.math.BigDecimal;

/**
 * A single envelope's share within a distribution result.
 * {@code balance} is null for dry-run (persist=false) responses.
 */
public record DistributionLine(
        Long envelopeId,
        String envelopeName,
        BigDecimal percentage,
        BigDecimal allocated,
        BigDecimal balance) {
}
