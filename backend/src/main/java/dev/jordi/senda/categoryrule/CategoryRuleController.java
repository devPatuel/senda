package dev.jordi.senda.categoryrule;

import dev.jordi.senda.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/category-rules")
public class CategoryRuleController {

    private final CategoryRuleService service;

    public CategoryRuleController(CategoryRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryRuleResponse> list() {
        return service.list(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryRuleResponse create(@Valid @RequestBody CategoryRuleRequest request) {
        return service.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public CategoryRuleResponse update(@PathVariable Long id,
                                       @Valid @RequestBody CategoryRuleRequest request) {
        return service.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }
}
