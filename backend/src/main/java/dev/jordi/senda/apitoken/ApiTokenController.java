package dev.jordi.senda.apitoken;

import dev.jordi.senda.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tokens")
public class ApiTokenController {

    private final ApiTokenService service;

    public ApiTokenController(ApiTokenService service) {
        this.service = service;
    }

    /** Returns the clear token value ONCE, in the {@code value} field. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiTokenService.GeneratedToken create(@Valid @RequestBody CreateTokenRequest request) {
        return service.generate(CurrentUser.id(), request.name());
    }

    @GetMapping
    public List<TokenResponse> list() {
        return service.list(CurrentUser.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id) {
        service.revoke(CurrentUser.id(), id);
    }
}
