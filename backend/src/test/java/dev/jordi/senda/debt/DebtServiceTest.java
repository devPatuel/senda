package dev.jordi.senda.debt;

import dev.jordi.senda.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 16);

    @Mock
    private DebtRepository debtRepository;
    @Mock
    private DebtPaymentRepository paymentRepository;

    private DebtService service;

    @BeforeEach
    void setUp() {
        service = new DebtService(debtRepository, paymentRepository);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Debt debt(Long id, BigDecimal originalAmount) {
        Debt d = new Debt(USER_ID, DebtDirection.THEY_OWE_ME, "Ana", "Cena", originalAmount, TODAY);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private static DebtPayment payment(Long id, Long debtId, BigDecimal amount) {
        DebtPayment p = new DebtPayment(debtId, USER_ID, amount, TODAY, null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static DebtRequest createRequest(BigDecimal originalAmount) {
        return new DebtRequest(DebtDirection.THEY_OWE_ME, "Ana", "Cena", originalAmount, TODAY);
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    void createSavesDebtAndMapsResponse() {
        when(debtRepository.save(any(Debt.class))).thenAnswer(invocation -> {
            Debt saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        DebtResponse response = service.create(USER_ID, createRequest(new BigDecimal("100.00")));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.direction()).isEqualTo(DebtDirection.THEY_OWE_ME);
        assertThat(response.counterparty()).isEqualTo("Ana");
        assertThat(response.originalAmount()).isEqualByComparingTo("100.00");
        assertThat(response.paidAmount()).isEqualByComparingTo("0.00");
        assertThat(response.pendingAmount()).isEqualByComparingTo("100.00");
        assertThat(response.settled()).isFalse();

        ArgumentCaptor<Debt> captor = ArgumentCaptor.forClass(Debt.class);
        verify(debtRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    // -------------------------------------------------------------------------
    // get — foreign/missing debt
    // -------------------------------------------------------------------------

    @Test
    void getForeignOrMissingDebtThrowsNotFound() {
        when(debtRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // addPayment
    // -------------------------------------------------------------------------

    @Test
    void paymentExceedingPendingThrowsInvalidDebt() {
        Debt d = debt(10L, new BigDecimal("100.00"));
        when(debtRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(d));
        // 30 already paid -> pending = 70
        when(paymentRepository.sumByDebtId(10L)).thenReturn(new BigDecimal("30.00"));

        DebtPaymentRequest request = new DebtPaymentRequest(
                new BigDecimal("71.00"), TODAY, null); // exceeds 70

        assertThatThrownBy(() -> service.addPayment(USER_ID, 10L, request))
                .isInstanceOf(InvalidDebtException.class)
                .hasMessageContaining("Payment exceeds the pending amount");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void paymentBringsDebtToSettled() {
        Debt d = debt(10L, new BigDecimal("100.00"));
        when(debtRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(d));
        when(paymentRepository.sumByDebtId(10L)).thenReturn(new BigDecimal("30.00"));
        when(paymentRepository.save(any(DebtPayment.class))).thenAnswer(inv -> {
            DebtPayment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });

        DebtPaymentRequest request = new DebtPaymentRequest(
                new BigDecimal("70.00"), TODAY, null); // exactly the remaining amount

        service.addPayment(USER_ID, 10L, request);

        assertThat(d.isSettled()).isTrue();
    }

    @Test
    void partialPaymentDoesNotSettle() {
        Debt d = debt(10L, new BigDecimal("100.00"));
        when(debtRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(d));
        when(paymentRepository.sumByDebtId(10L)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(DebtPayment.class))).thenAnswer(inv -> {
            DebtPayment p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });

        service.addPayment(USER_ID, 10L, new DebtPaymentRequest(new BigDecimal("30.00"), TODAY, null));

        assertThat(d.isSettled()).isFalse();
    }

    // -------------------------------------------------------------------------
    // deletePayment — un-settle
    // -------------------------------------------------------------------------

    @Test
    void deletingPaymentRevertsSettledToFalse() {
        Debt d = debt(10L, new BigDecimal("100.00"));
        d.setSettled(true);
        DebtPayment p = payment(5L, 10L, new BigDecimal("100.00"));

        when(debtRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(d));
        when(paymentRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(p));
        // After deletion the DB sum would be 0; simulate that the sum call
        // returns the value BEFORE deletion (the service subtracts p.amount)
        when(paymentRepository.sumByDebtId(10L)).thenReturn(new BigDecimal("100.00"));

        service.deletePayment(USER_ID, 10L, 5L);

        assertThat(d.isSettled()).isFalse();
        verify(paymentRepository).delete(p);
    }

    @Test
    void deletePaymentForForeignDebtThrowsNotFound() {
        when(debtRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePayment(USER_ID, 10L, 5L))
                .isInstanceOf(NotFoundException.class);
        verify(paymentRepository, never()).delete(any());
    }

    @Test
    void deletePaymentWithMismatchedDebtIdThrowsNotFound() {
        Debt d = debt(10L, new BigDecimal("100.00"));
        // Payment belongs to a DIFFERENT debt (debtId = 99)
        DebtPayment p = payment(5L, 99L, new BigDecimal("50.00"));

        when(debtRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(d));
        when(paymentRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.deletePayment(USER_ID, 10L, 5L))
                .isInstanceOf(NotFoundException.class);
        verify(paymentRepository, never()).delete(any());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void updateWithNewOriginalLessThanPaidThrowsInvalidDebt() {
        Debt d = debt(10L, new BigDecimal("100.00"));
        when(debtRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(d));
        when(paymentRepository.sumByDebtId(10L)).thenReturn(new BigDecimal("80.00"));

        DebtRequest request = new DebtRequest(
                DebtDirection.THEY_OWE_ME, "Ana", "Cena", new BigDecimal("50.00"), TODAY);

        assertThatThrownBy(() -> service.update(USER_ID, 10L, request))
                .isInstanceOf(InvalidDebtException.class);
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    @Test
    void listReturnsDebtsMappedWithPaidAndPending() {
        Debt d1 = debt(1L, new BigDecimal("100.00"));
        Debt d2 = debt(2L, new BigDecimal("50.00"));
        when(debtRepository.findByUserId(USER_ID)).thenReturn(List.of(d1, d2));
        // list() uses the batched sum query to avoid an N+1. d2 has no payments, so
        // it is simply absent from the result rows (defaults to zero in the service).
        when(paymentRepository.sumByDebtIds(USER_ID, List.of(1L, 2L))).thenReturn(List.<Object[]>of(
                new Object[]{1L, new BigDecimal("30.00")}));

        List<DebtResponse> result = service.list(USER_ID, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).pendingAmount()).isEqualByComparingTo("70.00");
        assertThat(result.get(1).pendingAmount()).isEqualByComparingTo("50.00");
    }
}
