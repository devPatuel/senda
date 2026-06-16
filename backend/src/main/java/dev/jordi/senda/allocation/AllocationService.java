package dev.jordi.senda.allocation;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AllocationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AllocationEnvelopeRepository envelopeRepository;
    private final EnvelopeBalanceRepository balanceRepository;

    public AllocationService(AllocationEnvelopeRepository envelopeRepository,
                             EnvelopeBalanceRepository balanceRepository) {
        this.envelopeRepository = envelopeRepository;
        this.balanceRepository = balanceRepository;
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EnvelopeResponse> list(Long userId) {
        List<AllocationEnvelope> envelopes = envelopeRepository.findByUserIdOrderByPosition(userId);

        // Build a lookup map so we avoid N+1 queries
        Map<Long, BigDecimal> balances = balanceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(EnvelopeBalance::getEnvelopeId, EnvelopeBalance::getBalance));

        return envelopes.stream()
                .map(e -> EnvelopeResponse.from(e, balances.getOrDefault(e.getId(), BigDecimal.ZERO.setScale(2))))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Save plan (atomic replace)
    // -------------------------------------------------------------------------

    /**
     * Replaces the user's envelope plan atomically.
     *
     * <p>Strategy: keep existing envelopes (by id) to preserve their accumulated
     * balances; delete envelopes that are no longer in the list; create new
     * envelopes (id == null) with balance 0. This lets the user rename or
     * reorder without losing history.
     *
     * <p>The sum of all percentages MUST equal exactly 100, checked via
     * {@code BigDecimal.compareTo} to avoid scale mismatches (e.g. 100 vs 100.00).
     */
    @Transactional
    public List<EnvelopeResponse> savePlan(Long userId, EnvelopePlanRequest request) {
        validateSum(request.envelopes());

        List<AllocationEnvelope> existing = envelopeRepository.findByUserIdOrderByPosition(userId);
        Map<Long, AllocationEnvelope> byId = existing.stream()
                .collect(Collectors.toMap(AllocationEnvelope::getId, e -> e));

        // Ids that survive in the new plan
        Set<Long> incomingIds = request.envelopes().stream()
                .map(EnvelopeLineRequest::id)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // Authorize EVERY supplied id BEFORE any mutation. byId only holds this
        // user's envelopes, so a foreign or unknown id fails here, before we run
        // any destructive DELETE. Never rely on the transaction rollback to undo
        // writes made before an authorization check.
        for (Long id : incomingIds) {
            if (!byId.containsKey(id)) {
                throw new NotFoundException("Envelope not found");
            }
        }

        // Delete envelopes removed from the plan (cascade deletes their balance rows)
        existing.stream()
                .filter(e -> !incomingIds.contains(e.getId()))
                .forEach(envelopeRepository::delete);
        envelopeRepository.flush();

        List<AllocationEnvelope> result = new ArrayList<>();
        List<EnvelopeLineRequest> lines = request.envelopes();

        for (int i = 0; i < lines.size(); i++) {
            EnvelopeLineRequest line = lines.get(i);

            if (line.id() != null) {
                // Update existing envelope — verify it belongs to this user
                AllocationEnvelope envelope = Optional.ofNullable(byId.get(line.id()))
                        .orElseThrow(() -> new NotFoundException("Envelope not found"));
                if (!envelope.getUserId().equals(userId)) {
                    throw new NotFoundException("Envelope not found");
                }
                envelope.setName(line.name());
                envelope.setPercentage(line.percentage());
                envelope.setPosition(i);
                result.add(envelope);
            } else {
                // Create new envelope and its balance row (starting at 0)
                AllocationEnvelope envelope = envelopeRepository.save(
                        new AllocationEnvelope(userId, line.name(), line.percentage(), i));
                balanceRepository.save(new EnvelopeBalance(envelope.getId(), userId));
                result.add(envelope);
            }
        }

        // Ensure balance rows exist for kept envelopes (idempotent)
        for (AllocationEnvelope envelope : result) {
            if (balanceRepository.findByEnvelopeId(envelope.getId()).isEmpty()) {
                balanceRepository.save(new EnvelopeBalance(envelope.getId(), userId));
            }
        }

        Map<Long, BigDecimal> balances = balanceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(EnvelopeBalance::getEnvelopeId, EnvelopeBalance::getBalance));

        return result.stream()
                .map(e -> EnvelopeResponse.from(e, balances.getOrDefault(e.getId(), BigDecimal.ZERO.setScale(2))))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Distribute (simulate or persist)
    // -------------------------------------------------------------------------

    /**
     * Distributes {@code amount} across envelopes proportionally to their percentages.
     *
     * <p>Rounding: each line is rounded HALF_UP to 2 decimals. The last envelope
     * absorbs the rounding residual so that {@code sum(allocated) == amount} exactly.
     * This avoids losing or creating cents.
     *
     * <p>When {@code persist} is {@code true} each allocated amount is added to
     * the corresponding {@link EnvelopeBalance} row inside the same transaction.
     */
    @Transactional
    public DistributionResponse distribute(Long userId, DistributeRequest request) {
        List<AllocationEnvelope> envelopes = envelopeRepository.findByUserIdOrderByPosition(userId);

        if (envelopes.isEmpty()) {
            throw new InvalidAllocationException("No envelopes defined. Save a plan first.");
        }

        // Guard: the current plan must sum to 100 before distributing
        validateSum(envelopes.stream()
                .map(e -> new EnvelopeLineRequest(e.getId(), e.getName(), e.getPercentage()))
                .toList());

        BigDecimal amount = request.amount();
        List<BigDecimal> rawAllocations = envelopes.stream()
                .map(e -> amount.multiply(e.getPercentage())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP))
                .toList();

        // Rounding correction: adjust the last envelope so the sum is exact
        BigDecimal allocated = rawAllocations.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal residual = amount.subtract(allocated);

        List<BigDecimal> finalAllocations = new ArrayList<>(rawAllocations);
        if (residual.compareTo(BigDecimal.ZERO) != 0) {
            int last = finalAllocations.size() - 1;
            finalAllocations.set(last, finalAllocations.get(last).add(residual));
        }

        // Load balances for response (and optional mutation)
        Map<Long, EnvelopeBalance> balanceByEnvelopeId = balanceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(EnvelopeBalance::getEnvelopeId, b -> b));

        List<DistributionLine> lines = new ArrayList<>();
        for (int i = 0; i < envelopes.size(); i++) {
            AllocationEnvelope envelope = envelopes.get(i);
            BigDecimal share = finalAllocations.get(i);
            EnvelopeBalance balanceRow = balanceByEnvelopeId.get(envelope.getId());

            BigDecimal newBalance = null;
            if (request.persist() && balanceRow != null) {
                BigDecimal updated = balanceRow.getBalance().add(share);
                balanceRow.setBalance(updated);
                newBalance = updated;
            } else if (balanceRow != null) {
                newBalance = balanceRow.getBalance();
            }

            lines.add(new DistributionLine(
                    envelope.getId(),
                    envelope.getName(),
                    envelope.getPercentage(),
                    share,
                    newBalance));
        }

        return new DistributionResponse(amount, lines);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void validateSum(List<EnvelopeLineRequest> lines) {
        BigDecimal sum = lines.stream()
                .map(EnvelopeLineRequest::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(HUNDRED) != 0) {
            throw new ConflictException(
                    "Envelope percentages must sum to exactly 100 (current sum: " + sum.toPlainString() + ")");
        }
    }
}
