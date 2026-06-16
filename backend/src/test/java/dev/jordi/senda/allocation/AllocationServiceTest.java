package dev.jordi.senda.allocation;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private AllocationEnvelopeRepository envelopeRepository;

    @Mock
    private EnvelopeBalanceRepository balanceRepository;

    private AllocationService service;

    @BeforeEach
    void setUp() {
        service = new AllocationService(envelopeRepository, balanceRepository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AllocationEnvelope envelope(Long id, Long userId, String name, String pct, int pos) {
        AllocationEnvelope e = new AllocationEnvelope(userId, name, new BigDecimal(pct), pos);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private static EnvelopeBalance balance(Long envelopeId, Long userId, String amount) {
        EnvelopeBalance b = new EnvelopeBalance(envelopeId, userId);
        b.setBalance(new BigDecimal(amount));
        ReflectionTestUtils.setField(b, "id", envelopeId * 10);
        return b;
    }

    private static EnvelopeLineRequest line(Long id, String name, String pct) {
        return new EnvelopeLineRequest(id, name, new BigDecimal(pct));
    }

    // -------------------------------------------------------------------------
    // savePlan — validation
    // -------------------------------------------------------------------------

    @Test
    void savePlanThrowsConflictWhenPercentagesDontSumTo100() {
        EnvelopePlanRequest request = new EnvelopePlanRequest(List.of(
                line(null, "Ahorro", "50"),
                line(null, "Ocio", "30")));  // sum = 80, not 100

        assertThatThrownBy(() -> service.savePlan(USER_ID, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("100");

        verify(envelopeRepository, never()).save(any());
    }

    @Test
    void savePlanAcceptsExactly100() {
        EnvelopePlanRequest request = new EnvelopePlanRequest(List.of(
                line(null, "Ahorro", "50"),
                line(null, "Inversión", "20"),
                line(null, "Ocio", "30")));

        when(envelopeRepository.findByUserIdOrderByPosition(USER_ID)).thenReturn(List.of());
        when(envelopeRepository.save(any())).thenAnswer(inv -> {
            AllocationEnvelope e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", (long) (Math.random() * 1000 + 1));
            return e;
        });
        when(balanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(balanceRepository.findByEnvelopeId(any())).thenReturn(Optional.empty());
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<EnvelopeResponse> result = service.savePlan(USER_ID, request);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(EnvelopeResponse::name)
                .containsExactly("Ahorro", "Inversión", "Ocio");
    }

    // -------------------------------------------------------------------------
    // distribute — rounding and cent adjustment
    // -------------------------------------------------------------------------

    @Test
    void distributeReturnsCorrectSplitAndAdjustsCentsOnLastEnvelope() {
        // 33.33 + 33.33 + 33.34 = 100.00 exactly (classic rounding problem)
        AllocationEnvelope e1 = envelope(1L, USER_ID, "A", "33.33", 0);
        AllocationEnvelope e2 = envelope(2L, USER_ID, "B", "33.33", 1);
        AllocationEnvelope e3 = envelope(3L, USER_ID, "C", "33.34", 2);

        when(envelopeRepository.findByUserIdOrderByPosition(USER_ID)).thenReturn(List.of(e1, e2, e3));
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(List.of(
                balance(1L, USER_ID, "0.00"),
                balance(2L, USER_ID, "0.00"),
                balance(3L, USER_ID, "0.00")));

        DistributionResponse resp = service.distribute(USER_ID,
                new DistributeRequest(new BigDecimal("100.00"), false));

        List<DistributionLine> lines = resp.lines();
        BigDecimal total = lines.stream()
                .map(DistributionLine::allocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total).isEqualByComparingTo("100.00");

        // The first two get exactly 33.33 each; the last absorbs the residual to sum to 100
        assertThat(lines.get(0).allocated()).isEqualByComparingTo("33.33");
        assertThat(lines.get(1).allocated()).isEqualByComparingTo("33.33");
        // Third envelope: 100 - 33.33 - 33.33 = 33.34
        assertThat(lines.get(2).allocated()).isEqualByComparingTo("33.34");
    }

    @Test
    void distributeWithPersistAccumulatesBalances() {
        AllocationEnvelope e1 = envelope(1L, USER_ID, "Ahorro", "60", 0);
        AllocationEnvelope e2 = envelope(2L, USER_ID, "Ocio", "40", 1);

        EnvelopeBalance b1 = balance(1L, USER_ID, "100.00");
        EnvelopeBalance b2 = balance(2L, USER_ID, "50.00");

        when(envelopeRepository.findByUserIdOrderByPosition(USER_ID)).thenReturn(List.of(e1, e2));
        when(balanceRepository.findByUserId(USER_ID)).thenReturn(List.of(b1, b2));

        service.distribute(USER_ID, new DistributeRequest(new BigDecimal("1000.00"), true));

        // Balances should be updated in-place (dirty checking)
        assertThat(b1.getBalance()).isEqualByComparingTo("700.00");   // 100 + 600
        assertThat(b2.getBalance()).isEqualByComparingTo("450.00");   // 50 + 400
    }

    @Test
    void distributeThrowsConflictWhenPlanDoesNotSumTo100() {
        // Simulate a corrupted plan (should never happen through normal API but guard anyway)
        AllocationEnvelope e1 = envelope(1L, USER_ID, "Ahorro", "50", 0);

        when(envelopeRepository.findByUserIdOrderByPosition(USER_ID)).thenReturn(List.of(e1));

        assertThatThrownBy(() -> service.distribute(USER_ID,
                new DistributeRequest(new BigDecimal("1000.00"), false)))
                .isInstanceOf(ConflictException.class);
    }

    // -------------------------------------------------------------------------
    // Isolation: foreign envelope returns 404
    // -------------------------------------------------------------------------

    @Test
    void savePlanReturns404WhenEnvelopeIdBelongsToAnotherUser() {
        AllocationEnvelope foreign = envelope(99L, OTHER_USER_ID, "Ajeno", "100", 0);

        when(envelopeRepository.findByUserIdOrderByPosition(USER_ID)).thenReturn(List.of());

        // byId map will be empty (no envelopes for USER_ID), but request references id=99
        EnvelopePlanRequest request = new EnvelopePlanRequest(List.of(
                line(99L, "Hack", "100")));

        assertThatThrownBy(() -> service.savePlan(USER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void savePlanRejectsForeignIdBeforeDeletingOwnEnvelopes() {
        // The attacker DOES own an envelope (id=5) and sneaks a foreign id (id=99)
        // into the plan. Authorization must fail BEFORE any envelope is deleted —
        // we must not rely on the transaction rollback to undo destructive writes.
        AllocationEnvelope own = envelope(5L, USER_ID, "Mío", "50", 0);
        when(envelopeRepository.findByUserIdOrderByPosition(USER_ID)).thenReturn(List.of(own));

        EnvelopePlanRequest request = new EnvelopePlanRequest(List.of(
                line(5L, "Mío", "50"),
                line(99L, "Ajeno", "50")));

        assertThatThrownBy(() -> service.savePlan(USER_ID, request))
                .isInstanceOf(NotFoundException.class);

        // No envelope was deleted: the foreign id was rejected before any mutation
        verify(envelopeRepository, never()).delete(any());
    }
}
