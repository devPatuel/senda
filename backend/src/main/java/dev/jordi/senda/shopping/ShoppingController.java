package dev.jordi.senda.shopping;

import dev.jordi.senda.common.CurrentUser;
import dev.jordi.senda.common.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

import java.util.List;

@RestController
@RequestMapping("/api/shopping")
public class ShoppingController {

    private final ShoppingService shoppingService;

    public ShoppingController(ShoppingService shoppingService) {
        this.shoppingService = shoppingService;
    }

    @GetMapping("/items")
    public List<ShoppingItemResponse> list(
            @RequestParam(required = false) ShoppingListType listType) {
        return shoppingService.list(CurrentUser.id(), listType);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingItemResponse create(@Valid @RequestBody ShoppingItemRequest request) {
        return shoppingService.create(CurrentUser.id(), request);
    }

    @PutMapping("/items/{id}")
    public ShoppingItemResponse update(@PathVariable Long id,
                                       @Valid @RequestBody ShoppingItemRequest request) {
        return shoppingService.update(CurrentUser.id(), id, request);
    }

    @PatchMapping("/items/{id}/bought")
    public ShoppingItemResponse setBought(@PathVariable Long id,
                                          @Valid @RequestBody BoughtRequest request) {
        return shoppingService.setBought(CurrentUser.id(), id, request.bought());
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        shoppingService.delete(CurrentUser.id(), id);
    }

    /**
     * Controller-local handler: takes precedence over the global advice so
     * business-rule violations of this module map to 400 with a clear message.
     */
    @ExceptionHandler(InvalidShoppingException.class)
    public ResponseEntity<ErrorResponse> handleInvalidShopping(InvalidShoppingException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }
}
