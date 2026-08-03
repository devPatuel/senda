package dev.jordi.senda.habit;

import dev.jordi.senda.common.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/habits/{habitId}/entries")
public class HabitEntryController {

    private final HabitEntryService service;

    public HabitEntryController(HabitEntryService service) {
        this.service = service;
    }

    @PutMapping("/{date}")
    public HabitEntryResponse record(
            @PathVariable Long habitId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody HabitEntryRequest request) {
        return service.record(CurrentUser.id(), habitId, date, request);
    }

    @PostMapping("/{date}/increment")
    public HabitEntryResponse increment(
            @PathVariable Long habitId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody(required = false) IncrementRequest request) {
        BigDecimal amount = request == null ? null : request.amount();
        return service.increment(CurrentUser.id(), habitId, date, amount);
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(
            @PathVariable Long habitId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.clear(CurrentUser.id(), habitId, date);
    }

    /** Optional body; missing means one step. */
    public record IncrementRequest(BigDecimal amount) { }
}
