package dev.jordi.senda.account;

import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.space.SpaceAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class AccountService {

    private static final String DEFAULT_CURRENCY = "EUR";

    private final AccountRepository accountRepository;
    private final SpaceAccess spaceAccess;

    public AccountService(AccountRepository accountRepository, SpaceAccess spaceAccess) {
        this.accountRepository = accountRepository;
        this.spaceAccess = spaceAccess;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId, Long spaceId, boolean includeArchived) {
        List<Account> accounts;
        if (spaceId == null) {
            accounts = includeArchived
                    ? accountRepository.findByUserIdAndSpaceIdIsNull(userId)
                    : accountRepository.findByUserIdAndSpaceIdIsNullAndArchivedFalse(userId);
        } else {
            spaceAccess.assertActiveMember(userId, spaceId);
            accounts = includeArchived
                    ? accountRepository.findBySpaceId(spaceId)
                    : accountRepository.findBySpaceIdAndArchivedFalse(spaceId);
        }
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
        Account account = new Account(
                userId, request.name(), request.type(), request.balance(), currencyOrDefault(request.currency()));
        if (request.spaceId() != null) {
            spaceAccess.assertActiveMember(userId, request.spaceId());
            account.setSpaceId(request.spaceId());
        }
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse update(Long userId, Long id, AccountUpdateRequest request) {
        Account account = findAccessible(userId, id);
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
        accountRepository.delete(findAccessible(userId, id));
    }

    /**
     * Loads an account the caller may act on: a personal account they own, or a
     * shared account of a space they are an ACTIVE member of. 404 otherwise
     * (never 403: do not reveal the resource exists).
     */
    private Account findAccessible(Long userId, Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (account.getSpaceId() == null) {
            if (!account.getUserId().equals(userId)) {
                throw new NotFoundException("Account not found");
            }
        } else {
            spaceAccess.assertActiveMember(userId, account.getSpaceId());
        }
        return account;
    }

    private static String currencyOrDefault(String currency) {
        return currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency;
    }
}
