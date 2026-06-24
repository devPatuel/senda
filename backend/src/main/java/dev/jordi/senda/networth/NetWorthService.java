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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NetWorthService {

    static final int MAX_HISTORY_DAYS = 3650;

    private final AccountRepository accountRepository;
    private final HoldingRepository holdingRepository;
    private final NftRepository nftRepository;
    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;
    private final NetWorthSnapshotRepository snapshotRepository;

    public NetWorthService(AccountRepository accountRepository,
                           HoldingRepository holdingRepository,
                           NftRepository nftRepository,
                           DebtRepository debtRepository,
                           DebtPaymentRepository debtPaymentRepository,
                           NetWorthSnapshotRepository snapshotRepository) {
        this.accountRepository = accountRepository;
        this.holdingRepository = holdingRepository;
        this.nftRepository = nftRepository;
        this.debtRepository = debtRepository;
        this.debtPaymentRepository = debtPaymentRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public NetWorthResponse calculate(Long userId) {
        BigDecimal liquid = calculateLiquid(userId);
        BigDecimal investmentsHoldings = calculateHoldings(userId);
        BigDecimal investmentsNfts = calculateNfts(userId);
        BigDecimal investments = investmentsHoldings.add(investmentsNfts).setScale(2, RoundingMode.HALF_UP);
        BigDecimal debtsInFavor = calculateDebtsPending(userId, DebtDirection.THEY_OWE_ME)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal debtsAgainst = calculateDebtsPending(userId, DebtDirection.I_OWE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal net = liquid
                .add(investments)
                .add(debtsInFavor)
                .subtract(debtsAgainst)
                .setScale(2, RoundingMode.HALF_UP);

        recordDailySnapshot(userId, net, liquid, investments, debtsInFavor, debtsAgainst);

        return new NetWorthResponse(
                liquid,
                investments,
                investmentsHoldings.setScale(2, RoundingMode.HALF_UP),
                investmentsNfts.setScale(2, RoundingMode.HALF_UP),
                debtsInFavor,
                debtsAgainst,
                net);
    }

    /**
     * Net-worth history for the last {@code days} days (oldest first). Each day
     * has at most one snapshot; days the user did not open the app are simply
     * absent from the series.
     */
    @Transactional(readOnly = true)
    public List<NetWorthHistoryPoint> history(Long userId, int days) {
        int bounded = Math.min(Math.max(days, 1), MAX_HISTORY_DAYS);
        LocalDate from = LocalDate.now().minusDays(bounded - 1L);
        return snapshotRepository
                .findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(userId, from).stream()
                .map(s -> new NetWorthHistoryPoint(
                        s.getSnapshotDate(), s.getNet(), s.getLiquid(), s.getInvestments(),
                        s.getDebtsInFavor(), s.getDebtsAgainst()))
                .toList();
    }

    /**
     * Persists today's snapshot once per day (lazy, scheduler-free). The unique
     * (user, day) constraint is the real guard: a concurrent insert that slips
     * past the exists() check hits it and is swallowed, since the snapshot for
     * today already exists either way.
     */
    private void recordDailySnapshot(Long userId, BigDecimal net, BigDecimal liquid,
                                     BigDecimal investments, BigDecimal debtsInFavor, BigDecimal debtsAgainst) {
        LocalDate today = LocalDate.now();
        if (snapshotRepository.existsByUserIdAndSnapshotDate(userId, today)) {
            return;
        }
        try {
            snapshotRepository.save(new NetWorthSnapshot(
                    userId, today, net, liquid, investments, debtsInFavor, debtsAgainst));
        } catch (DataIntegrityViolationException ignored) {
            // A concurrent request already created today's snapshot; nothing to do.
        }
    }

    private BigDecimal calculateLiquid(Long userId) {
        BigDecimal balance = accountRepository.sumActiveBalance(userId);
        // sumActiveBalance returns null when the user has no active accounts
        return (balance != null ? balance : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateHoldings(Long userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        return holdings.stream()
                .map(h -> {
                    BigDecimal price = h.getCurrentPrice();
                    // Holdings without a market price contribute 0 to net worth
                    if (price == null) return BigDecimal.ZERO;
                    return h.getQuantity().multiply(price);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateNfts(Long userId) {
        List<Nft> nfts = nftRepository.findByUserId(userId);
        return nfts.stream()
                .map(Nft::getOurCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateDebtsPending(Long userId, DebtDirection direction) {
        List<Debt> debts = debtRepository.findByUserIdAndDirection(userId, direction);
        if (debts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Fetch paid totals for all debts in one query to avoid N+1
        List<Long> debtIds = debts.stream().map(Debt::getId).toList();
        List<Object[]> rows = debtPaymentRepository.sumByDebtIds(userId, debtIds);

        // Build a lookup map: debtId -> totalPaid
        Map<Long, BigDecimal> paid = rows.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (BigDecimal) row[1]));

        return debts.stream()
                .map(d -> {
                    BigDecimal totalPaid = paid.getOrDefault(d.getId(), BigDecimal.ZERO);
                    BigDecimal pending = d.getOriginalAmount().subtract(totalPaid);
                    // Settled debts have pending <= 0; clamp to 0 for safety
                    return pending.compareTo(BigDecimal.ZERO) > 0 ? pending : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
