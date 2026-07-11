package dev.jordi.senda.product;

import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final PriceEntryRepository priceEntryRepository;

    public ProductService(ProductRepository productRepository,
                          PriceEntryRepository priceEntryRepository) {
        this.productRepository = productRepository;
        this.priceEntryRepository = priceEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(Long userId) {
        List<Product> products = productRepository.findByUserIdAndSpaceIdIsNullOrderByNameAsc(userId);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        // Descending by recorded_at, so the first entry seen per (product, super)
        // is the current price. Grouped in memory: dataset is small (personal app).
        Map<Long, List<PriceEntry>> byProduct = new LinkedHashMap<>();
        for (PriceEntry e : priceEntryRepository.findByProductIdInOrderByRecordedAtDesc(ids)) {
            byProduct.computeIfAbsent(e.getProductId(), k -> new ArrayList<>()).add(e);
        }
        return products.stream()
                .map(p -> buildResponse(p, byProduct.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getOne(Long userId, Long id) {
        Product product = findOwned(userId, id);
        return buildResponse(product,
                priceEntryRepository.findByProductIdOrderByRecordedAtDesc(id));
    }

    @Transactional
    public ProductResponse create(Long userId, ProductRequest req) {
        Product product = new Product(userId, req.name(), req.unitType(), req.amount(), req.unit());
        return buildResponse(productRepository.save(product), List.of());
    }

    @Transactional
    public ProductResponse update(Long userId, Long id, ProductRequest req) {
        Product product = findOwned(userId, id);
        product.setName(req.name());
        product.setUnitType(req.unitType());
        product.setAmount(req.amount());
        product.setUnit(req.unit());
        return buildResponse(product,
                priceEntryRepository.findByProductIdOrderByRecordedAtDesc(id));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        // Deleting the product cascades its price history (FK ON DELETE CASCADE).
        productRepository.delete(findOwned(userId, id));
    }

    @Transactional
    public PriceEntryResponse addPrice(Long userId, Long productId, PriceEntryRequest req) {
        findOwned(userId, productId); // 404 if the product is not the user's
        PriceEntry entry = priceEntryRepository.save(
                new PriceEntry(productId, req.price(), req.supermarket().trim()));
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<PriceEntryResponse> priceHistory(Long userId, Long productId) {
        findOwned(userId, productId); // 404 if not owned
        return priceEntryRepository.findByProductIdOrderByRecordedAtDesc(productId).stream()
                .map(ProductService::toResponse)
                .toList();
    }

    private Product findOwned(Long userId, Long id) {
        // 404 (not 403): never reveal that the product belongs to another user.
        return productRepository.findByIdAndUserIdAndSpaceIdIsNull(id, userId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    // entries must be ordered by recorded_at DESC; keeps the first (latest) per super.
    private ProductResponse buildResponse(Product product, List<PriceEntry> entries) {
        Map<String, PriceEntry> latestPerSuper = new LinkedHashMap<>();
        for (PriceEntry e : entries) {
            latestPerSuper.putIfAbsent(e.getSupermarket(), e);
        }
        List<CurrentPriceResponse> current = latestPerSuper.values().stream()
                .sorted(Comparator.comparing(PriceEntry::getPrice)
                        .thenComparing(PriceEntry::getSupermarket, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new CurrentPriceResponse(e.getSupermarket(), e.getPrice(), e.getRecordedAt()))
                .toList();
        return new ProductResponse(
                product.getId(), product.getName(), product.getUnitType(),
                product.getAmount(), product.getUnit(), product.getCreatedAt(), current);
    }

    private static PriceEntryResponse toResponse(PriceEntry e) {
        return new PriceEntryResponse(e.getId(), e.getPrice(), e.getSupermarket(), e.getRecordedAt());
    }
}
