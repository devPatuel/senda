package dev.jordi.senda.networth;

import dev.jordi.senda.account.AccountRepository;
import dev.jordi.senda.debt.Debt;
import dev.jordi.senda.debt.DebtDirection;
import dev.jordi.senda.debt.DebtPaymentRepository;
import dev.jordi.senda.debt.DebtRepository;
import dev.jordi.senda.investment.Holding;
import dev.jordi.senda.investment.HoldingRepository;
import dev.jordi.senda.investment.Nft;
import dev.jordi.senda.investment.NftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetWorthServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private NftRepository nftRepository;
    @Mock
    private DebtRepository debtRepository;
    @Mock
    private DebtPaymentRepository debtPaymentRepository;

    private NetWorthService service;

    @BeforeEach
    void setUp() {
        service = new NetWorthService(
                accountRepository, holdingRepository, nftRepository,
                debtRepository, debtPaymentRepository);
    }

    // --- helpers ---

    private static Holding holding(Long id, String quantity, String price) {
        Holding h = new Holding(USER_ID, null, "SYM", "Name",
                new BigDecimal(quantity), new BigDecimal("100.00"));
        ReflectionTestUtils.setField(h, "id", id);
        if (price != null) {
            h.setCurrentPrice(new BigDecimal(price));
        }
        return h;
    }

    private static Nft nft(Long id, String ourCurrentValue) {
        Nft n = new Nft(USER_ID, "NFT", "Collection", "ETH",
                new BigDecimal("1.0"), new BigDecimal("1000.00"),
                new BigDecimal(ourCurrentValue), null);
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    private static Debt debt(Long id, DebtDirection direction, String originalAmount) {
        Debt d = new Debt(USER_ID, direction, "Counterparty", "Concept",
                new BigDecimal(originalAmount), LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    // --- full scenario ---

    @Test
    void calculatesNetWorthWithAllComponents() {
        // Liquid: 3000.00
        when(accountRepository.sumActiveBalance(USER_ID)).thenReturn(new BigDecimal("3000.00"));

        // Holdings: BTC 2 units @ 20000 = 40000; ETH 5 units with no price -> 0
        Holding btc = holding(1L, "2", "20000");
        Holding eth = holding(2L, "5", null);
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of(btc, eth));

        // NFTs: one at 500.00
        when(nftRepository.findByUserId(USER_ID)).thenReturn(List.of(nft(1L, "500.00")));

        // Debts in favor: Laura owes 200, paid 50 -> pending 150
        Debt theyOweMe = debt(10L, DebtDirection.THEY_OWE_ME, "200.00");
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.THEY_OWE_ME))
                .thenReturn(List.of(theyOweMe));
        when(debtPaymentRepository.sumByDebtIds(USER_ID, List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, new BigDecimal("50.00")}));

        // Debts against: mortgage 1000, paid 300 -> pending 700
        Debt iOwe = debt(20L, DebtDirection.I_OWE, "1000.00");
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.I_OWE))
                .thenReturn(List.of(iOwe));
        when(debtPaymentRepository.sumByDebtIds(USER_ID, List.of(20L)))
                .thenReturn(List.<Object[]>of(new Object[]{20L, new BigDecimal("300.00")}));

        NetWorthResponse result = service.calculate(USER_ID);

        // liquid = 3000.00
        assertThat(result.liquid()).isEqualByComparingTo("3000.00");
        // holdings = 2 * 20000 = 40000
        assertThat(result.investmentsHoldings()).isEqualByComparingTo("40000.00");
        // nfts = 500
        assertThat(result.investmentsNfts()).isEqualByComparingTo("500.00");
        // investments = 40500
        assertThat(result.investments()).isEqualByComparingTo("40500.00");
        // debts in favor = 150
        assertThat(result.debtsInFavor()).isEqualByComparingTo("150.00");
        // debts against = 700
        assertThat(result.debtsAgainst()).isEqualByComparingTo("700.00");
        // net = 3000 + 40500 + 150 - 700 = 42950
        assertThat(result.net()).isEqualByComparingTo("42950.00");
        // structural invariant
        assertThat(result.net()).isEqualByComparingTo(
                result.liquid()
                        .add(result.investments())
                        .add(result.debtsInFavor())
                        .subtract(result.debtsAgainst()));
    }

    @Test
    void holdingWithoutPriceCountsAsZero() {
        when(accountRepository.sumActiveBalance(USER_ID)).thenReturn(BigDecimal.ZERO);
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of(holding(1L, "10", null)));
        when(nftRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.THEY_OWE_ME)).thenReturn(List.of());
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.I_OWE)).thenReturn(List.of());

        NetWorthResponse result = service.calculate(USER_ID);

        assertThat(result.investmentsHoldings()).isEqualByComparingTo("0.00");
        assertThat(result.investments()).isEqualByComparingTo("0.00");
    }

    @Test
    void debtWithNoPaymentsHasFullAmountAsPending() {
        when(accountRepository.sumActiveBalance(USER_ID)).thenReturn(BigDecimal.ZERO);
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(nftRepository.findByUserId(USER_ID)).thenReturn(List.of());

        Debt theyOweMe = debt(10L, DebtDirection.THEY_OWE_ME, "100.00");
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.THEY_OWE_ME))
                .thenReturn(List.of(theyOweMe));
        // No payment rows for this debt
        when(debtPaymentRepository.sumByDebtIds(USER_ID, List.of(10L))).thenReturn(List.of());

        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.I_OWE)).thenReturn(List.of());

        NetWorthResponse result = service.calculate(USER_ID);

        assertThat(result.debtsInFavor()).isEqualByComparingTo("100.00");
    }

    @Test
    void settledDebtContributesZeroPending() {
        when(accountRepository.sumActiveBalance(USER_ID)).thenReturn(BigDecimal.ZERO);
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(nftRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.THEY_OWE_ME)).thenReturn(List.of());

        // I owe 200; paid exactly 200 -> pending = 0
        Debt iOwe = debt(20L, DebtDirection.I_OWE, "200.00");
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.I_OWE))
                .thenReturn(List.of(iOwe));
        when(debtPaymentRepository.sumByDebtIds(USER_ID, List.of(20L)))
                .thenReturn(List.<Object[]>of(new Object[]{20L, new BigDecimal("200.00")}));

        NetWorthResponse result = service.calculate(USER_ID);

        assertThat(result.debtsAgainst()).isEqualByComparingTo("0.00");
        assertThat(result.net()).isEqualByComparingTo("0.00");
    }

    // --- all-empty scenario ---

    @Test
    void allEmptyReturnsAllZeros() {
        when(accountRepository.sumActiveBalance(USER_ID)).thenReturn(null);
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(nftRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.THEY_OWE_ME)).thenReturn(List.of());
        when(debtRepository.findByUserIdAndDirection(USER_ID, DebtDirection.I_OWE)).thenReturn(List.of());

        NetWorthResponse result = service.calculate(USER_ID);

        assertThat(result.liquid()).isEqualByComparingTo("0.00");
        assertThat(result.investments()).isEqualByComparingTo("0.00");
        assertThat(result.investmentsHoldings()).isEqualByComparingTo("0.00");
        assertThat(result.investmentsNfts()).isEqualByComparingTo("0.00");
        assertThat(result.debtsInFavor()).isEqualByComparingTo("0.00");
        assertThat(result.debtsAgainst()).isEqualByComparingTo("0.00");
        assertThat(result.net()).isEqualByComparingTo("0.00");
    }
}
