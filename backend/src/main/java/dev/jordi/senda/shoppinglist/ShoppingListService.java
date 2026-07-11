package dev.jordi.senda.shoppinglist;

import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.product.PriceEntry;
import dev.jordi.senda.product.PriceEntryRepository;
import dev.jordi.senda.product.Product;
import dev.jordi.senda.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ShoppingListService {

    private final ShoppingListItemRepository listRepository;
    private final ProductRepository productRepository;
    private final PriceEntryRepository priceEntryRepository;

    public ShoppingListService(ShoppingListItemRepository listRepository,
                               ProductRepository productRepository,
                               PriceEntryRepository priceEntryRepository) {
        this.listRepository = listRepository;
        this.productRepository = productRepository;
        this.priceEntryRepository = priceEntryRepository;
    }

    @Transactional(readOnly = true)
    public ShoppingListResponse get(Long userId) {
        List<ShoppingListItemResponse> items = listRepository.findByUserId(userId).stream()
                .map(item -> toResponse(item, userId))
                .toList();
        BigDecimal total = items.stream()
                .map(ShoppingListItemResponse::lineTotal)
                .filter(t -> t != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ShoppingListResponse(items, total);
    }

    @Transactional
    public ShoppingListItemResponse add(Long userId, AddToListRequest req) {
        // Ownership check on the catalog product: foreign/absent -> 404. Personal scope.
        productRepository.findByIdAndUserIdAndSpaceIdIsNull(req.productId(), userId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        listRepository.findByUserIdAndProductId(userId, req.productId())
                .ifPresent(existing -> { throw new InvalidShoppingListException("Product already in the list"); });

        int qty = req.quantity() == null ? 1 : req.quantity();
        ShoppingListItem item = listRepository.save(new ShoppingListItem(userId, req.productId(), qty));
        return toResponse(item, userId);
    }

    @Transactional
    public ShoppingListItemResponse update(Long userId, Long id, UpdateListItemRequest req) {
        ShoppingListItem item = findOwned(userId, id);
        if (req.quantity() != null) item.setQuantity(req.quantity());
        if (req.checked() != null) item.setChecked(req.checked());
        return toResponse(item, userId);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        listRepository.delete(findOwned(userId, id));
    }

    @Transactional
    public void clearChecked(Long userId) {
        listRepository.deleteAll(listRepository.findByUserIdAndCheckedTrue(userId));
    }

    private ShoppingListItem findOwned(Long userId, Long id) {
        return listRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Shopping list item not found"));
    }

    private ShoppingListItemResponse toResponse(ShoppingListItem item, Long userId) {
        // Product is owned by the user (checked on add); tolerate deletion gracefully.
        String name = productRepository.findByIdAndUserIdAndSpaceIdIsNull(item.getProductId(), userId)
                .map(Product::getName).orElse(null);
        // Estimated unit price = most recent recorded price for the product (any supermarket).
        PriceEntry latest = priceEntryRepository
                .findTopByProductIdOrderByRecordedAtDesc(item.getProductId())
                .orElse(null);
        BigDecimal unitPrice = latest == null ? null : latest.getPrice();
        String supermarket = latest == null ? null : latest.getSupermarket();
        BigDecimal lineTotal = unitPrice == null ? null
                : unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
        return new ShoppingListItemResponse(item.getId(), item.getProductId(), name,
                item.getQuantity(), item.isChecked(), unitPrice, supermarket, lineTotal);
    }
}
