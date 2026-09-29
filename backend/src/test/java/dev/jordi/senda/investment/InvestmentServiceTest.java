package dev.jordi.senda.investment;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private AssetClassRepository assetClassRepository;
    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private HoldingLotRepository holdingLotRepository;
    @Mock
    private NftRepository nftRepository;
    @Mock
    private PricingService pricingService;

    private InvestmentService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentService(assetClassRepository, holdingRepository,
                holdingLotRepository, nftRepository, pricingService);
    }

    private static AssetClass assetClass(Long id, String name, PricingSource source) {
        AssetClass assetClass = new AssetClass(USER_ID, name, source);
        ReflectionTestUtils.setField(assetClass, "id", id);
        return assetClass;
    }

    private static Holding holding(Long id, AssetClass assetClass, String qty, String avgCost) {
        Holding holding = new Holding(USER_ID, assetClass, "BTC", "Bitcoin",
                new BigDecimal(qty), new BigDecimal(avgCost));
        ReflectionTestUtils.setField(holding, "id", id);
        return holding;
    }

    // --- buy / weighted average cost (KEY case) ---

    @Test
    void addBuyRecalculatesWeightedAverageCostAndQuantity() {
        // Start: 2 BTC @ 10000 avg. Buy 1 BTC @ 16000.
        // newQty = 3; newAvg = (2*10000 + 1*16000) / 3 = 36000/3 = 12000
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        Holding existing = holding(10L, crypto, "2", "10000");
        when(holdingRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(existing));

        HoldingResponse response = service.addBuy(USER_ID, 10L,
                new BuyRequest(new BigDecimal("1"), new BigDecimal("16000"), LocalDate.of(2026, 6, 1)));

        assertThat(response.quantity()).isEqualByComparingTo("3");
        assertThat(response.avgCost()).isEqualByComparingTo("12000");
        assertThat(response.cost()).isEqualByComparingTo("36000");

        // A lot is persisted with the buy figures
        ArgumentCaptor<HoldingLot> lotCaptor = ArgumentCaptor.forClass(HoldingLot.class);
        verify(holdingLotRepository).save(lotCaptor.capture());
        HoldingLot lot = lotCaptor.getValue();
        assertThat(lot.getHoldingId()).isEqualTo(10L);
        assertThat(lot.getUserId()).isEqualTo(USER_ID);
        assertThat(lot.getQuantity()).isEqualByComparingTo("1");
        assertThat(lot.getUnitPrice()).isEqualByComparingTo("16000");
    }

    @Test
    void addBuyOnEmptyPositionSetsAvgCostToBuyPrice() {
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        Holding empty = holding(10L, crypto, "0", "0");
        when(holdingRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(empty));

        HoldingResponse response = service.addBuy(USER_ID, 10L,
                new BuyRequest(new BigDecimal("0.5"), new BigDecimal("20000"), LocalDate.of(2026, 6, 1)));

        assertThat(response.quantity()).isEqualByComparingTo("0.5");
        assertThat(response.avgCost()).isEqualByComparingTo("20000");
    }

    @Test
    void addBuyOnForeignHoldingThrowsNotFound() {
        when(holdingRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addBuy(USER_ID, 99L,
                new BuyRequest(BigDecimal.ONE, BigDecimal.TEN, LocalDate.now())))
                .isInstanceOf(NotFoundException.class);
        verify(holdingLotRepository, never()).save(any());
    }

    // --- market value & P&L ---

    @Test
    void marketValueAndPnlComputedFromCurrentPrice() {
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        Holding h = holding(10L, crypto, "2", "10000");
        h.setCurrentPrice(new BigDecimal("15000"));
        when(holdingRepository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(h));

        HoldingResponse response = service.getHolding(USER_ID, 10L);

        assertThat(response.marketValue()).isEqualByComparingTo("30000"); // 2 * 15000
        assertThat(response.cost()).isEqualByComparingTo("20000");        // 2 * 10000
        assertThat(response.pnl()).isEqualByComparingTo("10000");         // 30000 - 20000
        assertThat(response.pricingSource()).isEqualTo(PricingSource.CRYPTO);
        assertThat(response.assetClassName()).isEqualTo("Cripto");
    }

    @Test
    void unpricedHoldingHasNullMarketValueAndPnl() {
        AssetClass metal = assetClass(6L, "Oro", PricingSource.METAL);
        Holding h = holding(11L, metal, "3", "50");
        // no current price set
        when(holdingRepository.findByIdAndUserId(11L, USER_ID)).thenReturn(Optional.of(h));

        HoldingResponse response = service.getHolding(USER_ID, 11L);

        assertThat(response.currentPrice()).isNull();
        assertThat(response.marketValue()).isNull();
        assertThat(response.pnl()).isNull();
        assertThat(response.cost()).isEqualByComparingTo("150"); // cost still computed
    }

    // --- asset class deletion guarded by holdings ---

    @Test
    void deleteAssetClassWithHoldingsThrowsConflict() {
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        when(assetClassRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(crypto));
        when(holdingRepository.existsByAssetClassId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteAssetClass(USER_ID, 5L))
                .isInstanceOf(ConflictException.class);
        verify(assetClassRepository, never()).delete(any());
    }

    @Test
    void deleteAssetClassWithoutHoldingsRemovesIt() {
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        when(assetClassRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(crypto));
        when(holdingRepository.existsByAssetClassId(5L)).thenReturn(false);

        service.deleteAssetClass(USER_ID, 5L);

        verify(assetClassRepository).delete(crypto);
    }

    @Test
    void deleteForeignAssetClassThrowsNotFound() {
        when(assetClassRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAssetClass(USER_ID, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createAssetClassWithDuplicateNameThrowsConflict() {
        when(assetClassRepository.existsByUserIdAndName(USER_ID, "Cripto")).thenReturn(true);

        assertThatThrownBy(() -> service.createAssetClass(USER_ID,
                new AssetClassRequest("Cripto", PricingSource.CRYPTO)))
                .isInstanceOf(ConflictException.class);
        verify(assetClassRepository, never()).save(any());
    }

    // --- createHolding validates ownership of the asset class ---

    @Test
    void createHoldingOnForeignAssetClassThrowsNotFound() {
        when(assetClassRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createHolding(USER_ID,
                new HoldingRequest(7L, "BTC", "Bitcoin", BigDecimal.ZERO, BigDecimal.ZERO)))
                .isInstanceOf(NotFoundException.class);
        verify(holdingRepository, never()).save(any());
    }

    @Test
    void createHoldingWithCostButZeroQuantityThrowsInvalidInvestment() {
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        when(assetClassRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(crypto));

        assertThatThrownBy(() -> service.createHolding(USER_ID,
                new HoldingRequest(5L, "BTC", "Bitcoin", BigDecimal.ZERO, new BigDecimal("100"))))
                .isInstanceOf(InvalidInvestmentException.class);
        verify(holdingRepository, never()).save(any());
    }

    // --- refresh prices ---

    @Test
    void refreshPricesUpdatesCryptoHoldingsFromPricingService() {
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        Holding btc = holding(10L, crypto, "2", "10000");
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of(btc));
        when(pricingService.pricesInEur(PricingSource.CRYPTO, List.of("BTC")))
                .thenReturn(Map.of("BTC", new BigDecimal("18000")));

        List<HoldingResponse> result = service.refreshPrices(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPrice()).isEqualByComparingTo("18000");
        assertThat(result.get(0).marketValue()).isEqualByComparingTo("36000");
        assertThat(btc.getLastPricedAt()).isNotNull();
    }

    @Test
    void refreshPricesLeavesPriceUntouchedWhenProviderHasNone() {
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        Holding btc = holding(10L, crypto, "2", "10000");
        btc.setCurrentPrice(new BigDecimal("12000"));
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of(btc));
        when(pricingService.pricesInEur(PricingSource.CRYPTO, List.of("BTC"))).thenReturn(Map.of());

        List<HoldingResponse> result = service.refreshPrices(USER_ID);

        // Previous price is kept when no fresh price is available
        assertThat(result.get(0).currentPrice()).isEqualByComparingTo("12000");
    }

    @Test
    void refreshPricesAsksForAllCryptoSymbolsInOneBatchAndSkipsManualClasses() {
        AssetClass crypto = assetClass(5L, "Cripto", PricingSource.CRYPTO);
        AssetClass gold = assetClass(7L, "Oro", PricingSource.METAL);
        Holding btc = holding(10L, crypto, "2", "10000");
        Holding cro = holding(11L, crypto, "1000", "0.1");
        cro.setSymbol("cro");
        Holding xau = holding(12L, gold, "1", "2000");
        xau.setSymbol("XAU");
        xau.setCurrentPrice(new BigDecimal("2500"));
        when(holdingRepository.findByUserId(USER_ID)).thenReturn(List.of(btc, cro, xau));
        when(pricingService.pricesInEur(PricingSource.CRYPTO, List.of("BTC", "CRO")))
                .thenReturn(Map.of("BTC", new BigDecimal("18000"), "CRO", new BigDecimal("0.08")));

        service.refreshPrices(USER_ID);

        // Symbol lookup is case-insensitive: "cro" is priced from the "CRO" entry
        assertThat(btc.getCurrentPrice()).isEqualByComparingTo("18000");
        assertThat(cro.getCurrentPrice()).isEqualByComparingTo("0.08");
        assertThat(xau.getCurrentPrice()).isEqualByComparingTo("2500");
        verify(pricingService).pricesInEur(PricingSource.CRYPTO, List.of("BTC", "CRO"));
    }

    // --- NFT computed value ---

    @Test
    void nftCurrentPurchaseValueComputedFromCryptoPrice() {
        Nft nft = new Nft(USER_ID, "Punk", "CryptoPunks", "ETH",
                new BigDecimal("2"), new BigDecimal("4000.00"), new BigDecimal("5000.00"), "PFP");
        ReflectionTestUtils.setField(nft, "id", 20L);
        when(nftRepository.findByUserId(USER_ID)).thenReturn(List.of(nft));
        when(pricingService.priceInEur(PricingSource.CRYPTO, "ETH"))
                .thenReturn(Optional.of(new BigDecimal("3000")));

        List<NftResponse> result = service.listNfts(USER_ID);

        // 2 ETH * 3000 = 6000
        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPurchaseValue()).isEqualByComparingTo("6000");
    }

    @Test
    void nftCurrentPurchaseValueNullWhenNoCryptoPrice() {
        Nft nft = new Nft(USER_ID, "Punk", null, "ZZZ",
                new BigDecimal("2"), new BigDecimal("4000.00"), new BigDecimal("5000.00"), null);
        ReflectionTestUtils.setField(nft, "id", 20L);
        when(nftRepository.findByUserId(USER_ID)).thenReturn(List.of(nft));
        when(pricingService.priceInEur(PricingSource.CRYPTO, "ZZZ")).thenReturn(Optional.empty());

        List<NftResponse> result = service.listNfts(USER_ID);

        assertThat(result.get(0).currentPurchaseValue()).isNull();
    }
}
