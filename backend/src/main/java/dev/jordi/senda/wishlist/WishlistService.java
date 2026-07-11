package dev.jordi.senda.wishlist;

import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class WishlistService {

    private final WishlistItemRepository repository;

    public WishlistService(WishlistItemRepository repository) {
        this.repository = repository;
    }

    // priority asc (nulls last), then case-insensitive name
    private static final Comparator<WishlistItem> ORDER =
            Comparator.comparing(WishlistItem::getPriority,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(WishlistItem::getName, String.CASE_INSENSITIVE_ORDER);

    @Transactional(readOnly = true)
    public WishlistResponse get(Long userId) {
        List<WishlistItem> all = repository.findByUserId(userId);
        List<WishlistItemResponse> items = all.stream()
                .sorted(ORDER)
                .map(WishlistItemResponse::from)
                .toList();
        BigDecimal total = all.stream()
                .map(WishlistItem::getPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WishlistResponse(items, total);
    }

    @Transactional
    public WishlistItemResponse create(Long userId, WishlistItemRequest req) {
        WishlistItem item = new WishlistItem(userId, req.name(), blankToNull(req.imageUrl()),
                blankToNull(req.productUrl()), blankToNull(req.comment()), req.price(), req.priority());
        return WishlistItemResponse.from(repository.save(item));
    }

    @Transactional
    public WishlistItemResponse update(Long userId, Long id, WishlistItemRequest req) {
        WishlistItem item = findOwned(userId, id);
        item.setName(req.name());
        item.setImageUrl(blankToNull(req.imageUrl()));
        item.setProductUrl(blankToNull(req.productUrl()));
        item.setComment(blankToNull(req.comment()));
        item.setPrice(req.price());
        item.setPriority(req.priority());
        return WishlistItemResponse.from(item);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(findOwned(userId, id));
    }

    private WishlistItem findOwned(Long userId, Long id) {
        // 404 (not 403): never reveal another user's item exists.
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Wishlist item not found"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
