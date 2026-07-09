package dev.jordi.senda.account;

import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.space.SpaceAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SpaceAccess spaceAccess;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, spaceAccess);
    }

    private static Account account(Long id, String name, AccountType type, String balance, boolean archived) {
        Account account = new Account(USER_ID, name, type, new BigDecimal(balance), "EUR");
        ReflectionTestUtils.setField(account, "id", id);
        account.setArchived(archived);
        return account;
    }

    // --- create ---

    @Test
    void createSavesAccountWithDefaultCurrencyWhenBlank() {
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        AccountResponse response = service.create(USER_ID,
                new AccountRequest("Cuenta nómina", AccountType.BANK, new BigDecimal("1500.00"), null, null));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Cuenta nómina");
        assertThat(response.type()).isEqualTo(AccountType.BANK);
        assertThat(response.balance()).isEqualByComparingTo("1500.00");
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.archived()).isFalse();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void createKeepsExplicitCurrency() {
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        AccountResponse response = service.create(USER_ID,
                new AccountRequest("Efectivo", AccountType.CASH, new BigDecimal("50.00"), "USD", null));

        assertThat(response.currency()).isEqualTo("USD");
    }

    // --- update ---

    @Test
    void updateChangesFieldsAndArchives() {
        Account existing = account(10L, "Banco", AccountType.BANK, "100.00", false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(existing));

        AccountResponse response = service.update(USER_ID, 10L,
                new AccountUpdateRequest("Banco principal", AccountType.BANK,
                        new BigDecimal("250.50"), "EUR", true));

        assertThat(response.name()).isEqualTo("Banco principal");
        assertThat(response.balance()).isEqualByComparingTo("250.50");
        assertThat(response.archived()).isTrue();
    }

    @Test
    void updateKeepsArchivedWhenNull() {
        Account existing = account(10L, "Banco", AccountType.BANK, "100.00", true);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(existing));

        AccountResponse response = service.update(USER_ID, 10L,
                new AccountUpdateRequest("Banco", AccountType.BANK, new BigDecimal("100.00"), "EUR", null));

        // archived was true and the request omits it: must be preserved
        assertThat(response.archived()).isTrue();
    }

    @Test
    void updateForeignOrMissingAccountThrowsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, 99L,
                new AccountUpdateRequest("X", AccountType.BANK, BigDecimal.ONE, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // --- delete ---

    @Test
    void deleteRemovesOwnedAccount() {
        Account existing = account(10L, "Banco", AccountType.BANK, "100.00", false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(existing));

        service.delete(USER_ID, 10L);

        verify(accountRepository).delete(existing);
    }

    @Test
    void deleteForeignOrMissingAccountThrowsNotFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, 99L)).isInstanceOf(NotFoundException.class);
        verify(accountRepository, never()).delete(any());
    }

    // --- total balance ---

    @Test
    void totalBalanceReturnsAggregatedSum() {
        when(accountRepository.sumActiveBalance(USER_ID)).thenReturn(new BigDecimal("1550.00"));

        assertThat(service.totalBalance(USER_ID).total()).isEqualByComparingTo("1550.00");
    }

    @Test
    void totalBalanceWithNoAccountsReturnsZero() {
        when(accountRepository.sumActiveBalance(USER_ID)).thenReturn(null);

        assertThat(service.totalBalance(USER_ID).total()).isEqualByComparingTo("0.00");
    }

    // --- list ---

    @Test
    void listSortsByNameCaseInsensitive() {
        when(accountRepository.findByUserIdAndSpaceIdIsNullAndArchivedFalse(USER_ID)).thenReturn(List.of(
                account(1L, "Zelle", AccountType.BANK, "1.00", false),
                account(2L, "ahorro", AccountType.BANK, "2.00", false)));

        List<AccountResponse> result = service.list(USER_ID, null, false);

        assertThat(result).extracting(AccountResponse::name).containsExactly("ahorro", "Zelle");
    }

    @Test
    void listIncludeArchivedUsesFullQuery() {
        when(accountRepository.findByUserIdAndSpaceIdIsNull(USER_ID)).thenReturn(List.of(
                account(1L, "Banco", AccountType.BANK, "1.00", true)));

        List<AccountResponse> result = service.list(USER_ID, null, true);

        assertThat(result).hasSize(1);
        verify(accountRepository).findByUserIdAndSpaceIdIsNull(USER_ID);
    }

    // --- space-aware ---

    @Test
    void createInSpaceValidatesMembershipAndSetsSpaceId() {
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 5L);
            return a;
        });

        service.create(USER_ID, new AccountRequest("Común", AccountType.BANK,
                new BigDecimal("100.00"), "EUR", 7L));

        verify(spaceAccess).assertActiveMember(USER_ID, 7L);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getSpaceId()).isEqualTo(7L);
    }

    @Test
    void listPersonalUsesSpaceIdIsNullQuery() {
        when(accountRepository.findByUserIdAndSpaceIdIsNullAndArchivedFalse(USER_ID))
                .thenReturn(List.of());
        service.list(USER_ID, null, false);
        verify(accountRepository).findByUserIdAndSpaceIdIsNullAndArchivedFalse(USER_ID);
    }

    @Test
    void listSpaceValidatesMembershipAndUsesSpaceQuery() {
        when(accountRepository.findBySpaceIdAndArchivedFalse(7L)).thenReturn(List.of());
        service.list(USER_ID, 7L, false);
        verify(spaceAccess).assertActiveMember(USER_ID, 7L);
        verify(accountRepository).findBySpaceIdAndArchivedFalse(7L);
    }
}
