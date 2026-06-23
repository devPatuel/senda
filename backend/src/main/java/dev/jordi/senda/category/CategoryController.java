package dev.jordi.senda.category;

import dev.jordi.senda.common.CurrentUser;
import dev.jordi.senda.common.TransactionType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryService.list(CurrentUser.id(), type, includeInactive);
    }

    @GetMapping("/budget")
    public CategoryBudgetResponse budget() {
        return categoryService.budget(CurrentUser.id());
    }

    @PostMapping("/{id}/assign")
    public CategoryBudgetResponse assign(@PathVariable Long id,
                                         @Valid @RequestBody AssignRequest request) {
        return categoryService.assign(CurrentUser.id(), id, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id,
                                   @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        categoryService.delete(CurrentUser.id(), id);
    }
}
