package dev.jordi.senda.debt;

import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DebtService {

    private final DebtRepository debtRepository;
    private final DebtPaymentRepository paymentRepository;

    public DebtService(DebtRepository debtRepository, DebtPaymentRepository paymentRepository) {
        this.debtRepository = debtRepository;
        this.paymentRepository = paymentRepository;
    }

    // -------------------------------------------------------------------------
    // Debt CRUD
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DebtResponse> list(Long userId, DebtDirection direction, Boolean settled) {
        List<Debt> debts;
        if (direction != null && settled != null) {
            debts = debtRepository.findByUserIdAndDirectionAndSettled(userId, direction, settled);
        } else if (direction != null) {
            debts = debtRepository.findByUserIdAndDirection(userId, direction);
        } else if (settled != null) {
            debts = debtRepository.findByUserIdAndSettled(userId, settled);
        } else {
            debts = debtRepository.findByUserId(userId);
        }
        if (debts.isEmpty()) {
            return List.of();
        }
        // Single aggregate query for all debts instead of one sum per debt (no N+1)
        Map<Long, BigDecimal> paidByDebtId = paidAmounts(userId, debts.stream().map(Debt::getId).toList());
        return debts.stream()
                .map(d -> DebtResponse.from(d, paidByDebtId.getOrDefault(d.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional
    public DebtResponse create(Long userId, DebtRequest request) {
        Debt saved = debtRepository.save(new Debt(
                userId,
                request.direction(),
                request.counterparty(),
                request.concept(),
                request.originalAmount(),
                request.date()));
        return DebtResponse.from(saved, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public DebtResponse get(Long userId, Long id) {
        Debt debt = findOwned(userId, id);
        return DebtResponse.from(debt, paidAmount(id));
    }

    @Transactional
    public DebtResponse update(Long userId, Long id, DebtRequest request) {
        Debt debt = findOwned(userId, id);
        BigDecimal paid = paidAmount(id);

        // New originalAmount must not be less than what has already been paid
        if (request.originalAmount().compareTo(paid) < 0) {
            throw new InvalidDebtException(
                    "New original amount (" + request.originalAmount() +
                    ") cannot be less than the amount already paid (" + paid + ")");
        }

        debt.setDirection(request.direction());
        debt.setCounterparty(request.counterparty());
        debt.setConcept(request.concept());
        debt.setOriginalAmount(request.originalAmount());
        debt.setDate(request.date());
        // Recalculate settled after changing originalAmount
        debt.setSettled(paid.compareTo(request.originalAmount()) >= 0);

        return DebtResponse.from(debt, paid);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        debtRepository.delete(findOwned(userId, id));
        // debt_payments cascade on DB level (ON DELETE CASCADE)
    }

    // -------------------------------------------------------------------------
    // Payment sub-resource
    // -------------------------------------------------------------------------

    @Transactional
    public DebtPaymentResponse addPayment(Long userId, Long debtId, DebtPaymentRequest request) {
        Debt debt = findOwned(userId, debtId);
        BigDecimal paid = paidAmount(debtId);
        BigDecimal pending = debt.getOriginalAmount().subtract(paid);

        if (request.amount().compareTo(pending) > 0) {
            throw new InvalidDebtException(
                    "Payment exceeds the pending amount (pending: " + pending + ", requested: " + request.amount() + ")");
        }

        DebtPayment payment = paymentRepository.save(
                new DebtPayment(debtId, userId, request.amount(), request.date(), request.note()));

        // Recalculate settled. Persist explicitly: this method saves the payment
        // first and only then mutates the debt, so we do not rely on the order of
        // Hibernate's autoflush to flush the settled change.
        BigDecimal newPaid = paid.add(request.amount());
        debt.setSettled(newPaid.compareTo(debt.getOriginalAmount()) >= 0);
        debtRepository.save(debt);

        return DebtPaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<DebtPaymentResponse> listPayments(Long userId, Long debtId) {
        findOwned(userId, debtId); // 404 if foreign/missing
        return paymentRepository.findByDebtIdOrderByDateDescIdDesc(debtId)
                .stream()
                .map(DebtPaymentResponse::from)
                .toList();
    }

    @Transactional
    public void deletePayment(Long userId, Long debtId, Long paymentId) {
        Debt debt = findOwned(userId, debtId);

        DebtPayment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .filter(p -> p.getDebtId().equals(debtId))
                .orElseThrow(() -> new NotFoundException("Payment not found"));

        // Read the current sum BEFORE deleting, so the subtraction is correct
        BigDecimal paidBeforeDelete = paidAmount(debtId);
        paymentRepository.delete(payment);

        BigDecimal newPaid = paidBeforeDelete.subtract(payment.getAmount());
        debt.setSettled(newPaid.compareTo(debt.getOriginalAmount()) >= 0);
        debtRepository.save(debt);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Debt findOwned(Long userId, Long id) {
        // 404 (not 403) for another user's debt: do not reveal its existence
        return debtRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Debt not found"));
    }

    private BigDecimal paidAmount(Long debtId) {
        return paymentRepository.sumByDebtId(debtId);
    }

    private Map<Long, BigDecimal> paidAmounts(Long userId, List<Long> debtIds) {
        return paymentRepository.sumByDebtIds(userId, debtIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (BigDecimal) row[1]));
    }
}
