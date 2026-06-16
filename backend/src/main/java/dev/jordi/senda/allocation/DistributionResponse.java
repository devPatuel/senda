package dev.jordi.senda.allocation;

import java.math.BigDecimal;
import java.util.List;

public record DistributionResponse(
        BigDecimal amount,
        List<DistributionLine> lines) {
}
