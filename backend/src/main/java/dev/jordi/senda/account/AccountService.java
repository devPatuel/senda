package dev.jordi.senda.account;

import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class AccountService {

    private static final String DEFAULT_CURRENCY = "EUR";

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId, boolean includeArchived) {
        List<Account> accounts = includeArchived
                ? accountRepository.findByUserId(userId)
                : accountRepository.findByUserIdAndArchivedFalse(userId);
        return accounts.stream()
                .sorted(Comparator.comparing(Account::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Account::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TotalBalanceResponse totalBalance(Long userId) {
        // Aggregated in the DB; null (no active accounts) maps to zero
        BigDecimal total = accountRepository.sumActiveBalance(userId);
        return new TotalBalanceResponse(total != null ? total : BigDecimal.ZERO.setScale(2));
    }

    @Transactional
    public AccountResponse create(Long userId, AccountRequest request) {
        Account saved = accountRepository.save(new Account(
                userId, request.name(), request.type(), request.balance(), currencyOrDefault(request.currency())));
        return AccountResponse.from(saved);
    }

    @Transactional
    public AccountResponse update(Long userId, Long id, AccountUpdateRequest request) {
        Account account = findOwned(userId, id);
        account.setName(request.name());
        account.setType(request.type());
        account.setBalance(request.balance());
        account.setCurrency(currencyOrDefault(request.currency()));
        if (request.archived() != null) {
            account.setArchived(request.archived());
        }
        // Managed entity: JPA dirty checking flushes the update on commit
        return AccountResponse.from(account);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        accountRepository.delete(findOwned(userId, id));
    }

    private Account findOwned(Long userId, Long id) {
        // 404 (not 403) for another user's account: do not reveal its existence
        return accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private static String currencyOrDefault(String currency) {
        return currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency;
    }
}
