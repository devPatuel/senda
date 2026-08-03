package dev.jordi.senda.habit;

import dev.jordi.senda.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService service;
    private final HabitQueryService queries;

    public HabitController(HabitService service, HabitQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping("/today")
    public List<TodayHabitResponse> today() {
        return queries.today(CurrentUser.id());
    }

    @GetMapping
    public List<HabitResponse> list(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(CurrentUser.id(), includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitResponse create(@Valid @RequestBody HabitRequest request) {
        return service.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public HabitResponse update(@PathVariable Long id, @Valid @RequestBody HabitRequest request) {
        return service.update(CurrentUser.id(), id, request);
    }

    @PatchMapping("/{id}/archive")
    public HabitResponse archive(@PathVariable Long id,
                                 @RequestParam(defaultValue = "true") boolean archived) {
        return service.setArchived(CurrentUser.id(), id, archived);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/history")
    public HabitHistoryResponse history(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queries.history(CurrentUser.id(), id, from, to);
    }
}
