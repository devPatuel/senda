package dev.jordi.senda.investment;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class InvestmentService {

    // Quantities and prices are NUMERIC(20,8): the weighted-average cost is kept
    // at scale 8 with HALF_UP, matching the column definition.
    private static final int MONEY_SCALE = 8;

    private final AssetClassRepository assetClassRepository;
    private final HoldingRepository holdingRepository;
    private final HoldingLotRepository holdingLotRepository;
    private final NftRepository nftRepository;
    private final PricingService pricingService;

    public InvestmentService(AssetClassRepository assetClassRepository,
                             HoldingRepository holdingRepository,
                             HoldingLotRepository holdingLotRepository,
                             NftRepository nftRepository,
                             PricingService pricingService) {
        this.assetClassRepository = assetClassRepository;
        this.holdingRepository = holdingRepository;
        this.holdingLotRepository = holdingLotRepository;
        this.nftRepository = nftRepository;
        this.pricingService = pricingService;
    }

    // --- Asset classes ---

    @Transactional(readOnly = true)
    public List<AssetClassResponse> listAssetClasses(Long userId) {
        return assetClassRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(AssetClass::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AssetClass::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(AssetClassResponse::from)
                .toList();
    }

    @Transactional
    public AssetClassResponse createAssetClass(Long userId, AssetClassRequest request) {
        if (assetClassRepository.existsByUserIdAndName(userId, request.name())) {
            throw new ConflictException("An asset class with that name already exists");
        }
        AssetClass saved = assetClassRepository.save(
                new AssetClass(userId, request.name(), request.pricingSource()));
        return AssetClassResponse.from(saved);
    }

    @Transactional
    public AssetClassResponse updateAssetClass(Long userId, Long id, AssetClassRequest request) {
        AssetClass assetClass = findOwnedAssetClass(userId, id);
        if (!assetClass.getName().equals(request.name())
                && assetClassRepository.existsByUserIdAndName(userId, request.name())) {
            throw new ConflictException("An asset class with that name already exists");
        }
        // Changing the pricing source of a class that already has holdings would
        // silently re-classify how each one is valued (e.g. a CRYPTO holding that
        // stops auto-pricing). Block it; the user must move the holdings first.
        if (assetClass.getPricingSource() != request.pricingSource()
                && holdingRepository.existsByAssetClassId(assetClass.getId())) {
            throw new ConflictException("Cannot change pricing source of an asset class with holdings");
        }
        assetClass.setName(request.name());
        assetClass.setPricingSource(request.pricingSource());
        return AssetClassResponse.from(assetClass);
    }

    @Transactional
    public void deleteAssetClass(Long userId, Long id) {
        AssetClass assetClass = findOwnedAssetClass(userId, id);
        if (holdingRepository.existsByAssetClassId(assetClass.getId())) {
            throw new ConflictException("Asset class has holdings; move or delete them first");
        }
        assetClassRepository.delete(assetClass);
    }

    // --- Holdings ---

    @Transactional(readOnly = true)
    public List<HoldingResponse> listHoldings(Long userId, Long assetClassId) {
        List<Holding> holdings = assetClassId != null
                ? holdingRepository.findByUserIdAndAssetClassId(userId, assetClassId)
                : holdingRepository.findByUserId(userId);
        return holdings.stream()
                .sorted(Comparator.comparing(Holding::getSymbol, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Holding::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(InvestmentService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HoldingResponse getHolding(Long userId, Long id) {
        return toResponse(findOwnedHolding(userId, id));
    }

    @Transactional
    public HoldingResponse createHolding(Long userId, HoldingRequest request) {
        AssetClass assetClass = findOwnedAssetClass(userId, request.assetClassId());
        // A position holding nothing cannot carry a cost: it would corrupt P&L.
        // (quantity > 0 with avgCost 0 is fine: e.g. an airdrop received for free.)
        if (request.quantity().signum() == 0 && request.avgCost().signum() != 0) {
            throw new InvalidInvestmentException("avgCost must be 0 when quantity is 0");
        }
        Holding holding = new Holding(userId, assetClass, request.symbol(), request.name(),
                request.quantity(), request.avgCost());
        return toResponse(holdingRepository.save(holding));
    }

    @Transactional
    public HoldingResponse addBuy(Long userId, Long holdingId, BuyRequest request) {
        Holding holding = findOwnedHolding(userId, holdingId);

        BigDecimal oldQty = holding.getQuantity();
        BigDecimal oldAvg = holding.getAvgCost();
        BigDecimal buyQty = request.quantity();
        BigDecimal unitPrice = request.unitPrice();

        BigDecimal newQty = oldQty.add(buyQty);
        // Weighted average: (oldQty*oldAvg + buyQty*unitPrice) / newQty.
        // newQty > 0 always here because buyQty > 0 (validated) and oldQty >= 0.
        BigDecimal newCostBasis = oldQty.multiply(oldAvg).add(buyQty.multiply(unitPrice));
        BigDecimal newAvg = newCostBasis.divide(newQty, MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setQuantity(newQty);
        holding.setAvgCost(newAvg);

        // Derive the lot's user_id from the holding (single source of truth), not
        // from the request context, so a lot can never diverge from its holding's owner.
        holdingLotRepository.save(new HoldingLot(holding.getId(), holding.getUserId(), buyQty, unitPrice, request.date()));
        return toResponse(holding);
    }

    @Transactional(readOnly = true)
    public List<HoldingLotResponse> listLots(Long userId, Long holdingId) {
        // Validate ownership of the holding first (404 if foreign/missing)
        findOwnedHolding(userId, holdingId);
        return holdingLotRepository.findByHoldingIdAndUserIdOrderByDateDescIdDesc(holdingId, userId).stream()
                .map(HoldingLotResponse::from)
                .toList();
    }

    @Transactional
    public HoldingResponse setPrice(Long userId, Long holdingId, PriceRequest request) {
        Holding holding = findOwnedHolding(userId, holdingId);
        holding.setCurrentPrice(request.currentPrice());
        holding.setLastPricedAt(Instant.now());
        return toResponse(holding);
    }

    @Transactional
    public void deleteHolding(Long userId, Long id) {
        // holding_lots cascade in the DB (ON DELETE CASCADE)
        holdingRepository.delete(findOwnedHolding(userId, id));
    }

    /**
     * Refreshes CRYPTO holdings' prices from the pricing service and returns the
     * full holdings list. METAL/FUND/MANUAL keep their manual price.
     */
    @Transactional
    public List<HoldingResponse> refreshPrices(Long userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        Instant now = Instant.now();
        List<Holding> cryptoHoldings = holdings.stream()
                .filter(holding -> holding.getAssetClass().getPricingSource() == PricingSource.CRYPTO)
                .toList();
        List<String> symbols = cryptoHoldings.stream()
                .map(holding -> holding.getSymbol().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        // One batched lookup for every crypto holding instead of one request each
        Map<String, BigDecimal> prices = pricingService.pricesInEur(PricingSource.CRYPTO, symbols);
        for (Holding holding : cryptoHoldings) {
            BigDecimal price = prices.get(holding.getSymbol().toUpperCase(Locale.ROOT));
            if (price != null) {
                holding.setCurrentPrice(price);
                holding.setLastPricedAt(now);
            }
        }
        return holdings.stream()
                .sorted(Comparator.comparing(Holding::getSymbol, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Holding::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(InvestmentService::toResponse)
                .toList();
    }

    // --- NFTs ---

    @Transactional(readOnly = true)
    public List<NftResponse> listNfts(Long userId) {
        return nftRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Nft::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Nft::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NftResponse createNft(Long userId, NftRequest request) {
        Nft saved = nftRepository.save(new Nft(userId, request.name(), request.collection(),
                request.buyCryptoSymbol(), request.buyCryptoAmount(), request.fiatValueAtPurchase(),
                request.ourCurrentValue(), request.utility()));
        return toResponse(saved);
    }

    @Transactional
    public NftResponse updateNft(Long userId, Long id, NftRequest request) {
        Nft nft = findOwnedNft(userId, id);
        nft.setName(request.name());
        nft.setCollection(request.collection());
        nft.setBuyCryptoSymbol(request.buyCryptoSymbol());
        nft.setBuyCryptoAmount(request.buyCryptoAmount());
        nft.setFiatValueAtPurchase(request.fiatValueAtPurchase());
        nft.setOurCurrentValue(request.ourCurrentValue());
        nft.setUtility(request.utility());
        return toResponse(nft);
    }

    @Transactional
    public void deleteNft(Long userId, Long id) {
        nftRepository.delete(findOwnedNft(userId, id));
    }

    // --- Helpers ---

    private AssetClass findOwnedAssetClass(Long userId, Long id) {
        return assetClassRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Asset class not found"));
    }

    private Holding findOwnedHolding(Long userId, Long id) {
        return holdingRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Holding not found"));
    }

    private Nft findOwnedNft(Long userId, Long id) {
        return nftRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("NFT not found"));
    }

    private static HoldingResponse toResponse(Holding holding) {
        BigDecimal quantity = holding.getQuantity();
        BigDecimal avgCost = holding.getAvgCost();
        BigDecimal currentPrice = holding.getCurrentPrice();

        BigDecimal cost = quantity.multiply(avgCost);
        BigDecimal marketValue = currentPrice != null ? quantity.multiply(currentPrice) : null;
        BigDecimal pnl = marketValue != null ? marketValue.subtract(cost) : null;

        AssetClass assetClass = holding.getAssetClass();
        return new HoldingResponse(
                holding.getId(),
                assetClass.getId(),
                assetClass.getName(),
                assetClass.getPricingSource(),
                holding.getSymbol(),
                holding.getName(),
                quantity,
                avgCost,
                currentPrice,
                holding.getLastPricedAt(),
                marketValue,
                pnl,
                cost);
    }

    private NftResponse toResponse(Nft nft) {
        // "What does the crypto you paid cost today": amount * live crypto price.
        // null when no price is available (unmapped symbol, provider down...).
        BigDecimal currentPurchaseValue = pricingService
                .priceInEur(PricingSource.CRYPTO, nft.getBuyCryptoSymbol())
                .map(price -> nft.getBuyCryptoAmount().multiply(price))
                .orElse(null);
        return new NftResponse(
                nft.getId(),
                nft.getName(),
                nft.getCollection(),
                nft.getBuyCryptoSymbol(),
                nft.getBuyCryptoAmount(),
                nft.getFiatValueAtPurchase(),
                nft.getOurCurrentValue(),
                nft.getUtility(),
                currentPurchaseValue,
                nft.getCreatedAt());
    }
}
