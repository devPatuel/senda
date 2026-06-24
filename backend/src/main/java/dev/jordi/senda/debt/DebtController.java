package dev.jordi.senda.debt;

import dev.jordi.senda.common.CurrentUser;
import dev.jordi.senda.common.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/debts")
public class DebtController {

    private final DebtService debtService;

    public DebtController(DebtService debtService) {
        this.debtService = debtService;
    }

    @GetMapping
    public List<DebtResponse> list(
            @RequestParam(required = false) DebtDirection direction,
            @RequestParam(required = false) Boolean settled) {
        return debtService.list(CurrentUser.id(), direction, settled);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DebtResponse create(@Valid @RequestBody DebtRequest request) {
        return debtService.create(CurrentUser.id(), request);
    }

    @GetMapping("/{id}")
    public DebtResponse get(@PathVariable Long id) {
        return debtService.get(CurrentUser.id(), id);
    }

    @PutMapping("/{id}")
    public DebtResponse update(@PathVariable Long id, @Valid @RequestBody DebtRequest request) {
        return debtService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        debtService.delete(CurrentUser.id(), id);
    }

    // --- Payment sub-resource ---

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public DebtPaymentResponse addPayment(@PathVariable Long id,
                                          @Valid @RequestBody DebtPaymentRequest request) {
        return debtService.addPayment(CurrentUser.id(), id, request);
    }

    @GetMapping("/{id}/payments")
    public List<DebtPaymentResponse> listPayments(@PathVariable Long id) {
        return debtService.listPayments(CurrentUser.id(), id);
    }

    @DeleteMapping("/{id}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long id, @PathVariable Long paymentId) {
        debtService.deletePayment(CurrentUser.id(), id, paymentId);
    }

    /**
     * Controller-local handler: business-rule violations of this module map to
     * 400 Bad Request with a clear message.
     */
    @ExceptionHandler(InvalidDebtException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDebt(InvalidDebtException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }
}
