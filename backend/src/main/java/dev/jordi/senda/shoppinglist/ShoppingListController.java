package dev.jordi.senda.shoppinglist;

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

@RestController
@RequestMapping("/api/shopping-list")
public class ShoppingListController {

    private final ShoppingListService service;

    public ShoppingListController(ShoppingListService service) {
        this.service = service;
    }

    @GetMapping
    public ShoppingListResponse get() {
        return service.get(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListItemResponse add(@Valid @RequestBody AddToListRequest request) {
        return service.add(CurrentUser.id(), request);
    }

    // PUT (not PATCH): the frontend http client only exposes get/post/put/delete.
    @PutMapping("/{id}")
    public ShoppingListItemResponse update(@PathVariable Long id,
                                           @Valid @RequestBody UpdateListItemRequest request) {
        return service.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
    }

    @DeleteMapping("/checked")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearChecked() {
        service.clearChecked(CurrentUser.id());
    }

    @ExceptionHandler(InvalidShoppingListException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidShoppingListException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }
}
