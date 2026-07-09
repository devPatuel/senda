package dev.jordi.senda.account;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@RequestParam(required = false) Long spaceId,
                                      @RequestParam(defaultValue = "false") boolean includeArchived) {
        return accountService.list(CurrentUser.id(), spaceId, includeArchived);
    }

    @GetMapping("/balance")
    public TotalBalanceResponse balance() {
        return accountService.totalBalance(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest request) {
        return accountService.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable Long id,
                                  @Valid @RequestBody AccountUpdateRequest request) {
        return accountService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        accountService.delete(CurrentUser.id(), id);
    }
}
