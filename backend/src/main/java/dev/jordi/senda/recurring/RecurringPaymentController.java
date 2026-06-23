package dev.jordi.senda.recurring;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recurring")
public class RecurringPaymentController {

    private final RecurringPaymentService service;

    public RecurringPaymentController(RecurringPaymentService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecurringPaymentResponse> list() {
        return service.list(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringPaymentResponse create(@Valid @RequestBody RecurringPaymentRequest request) {
        return service.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public RecurringPaymentResponse update(@PathVariable Long id,
                                           @Valid @RequestBody RecurringPaymentRequest request) {
        return service.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }

    @ExceptionHandler(InvalidRecurringException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidRecurringException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }
}
