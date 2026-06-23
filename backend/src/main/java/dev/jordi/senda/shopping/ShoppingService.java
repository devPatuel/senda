package dev.jordi.senda.shopping;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryBalance;
import dev.jordi.senda.category.CategoryBalanceRepository;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class ShoppingService {

    // A shopping item's "envelope" is an expense category; envelopeId == categoryId.
    private final ShoppingItemRepository shoppingItemRepository;
    private final CategoryBalanceRepository categoryBalanceRepository;
    private final CategoryRepository categoryRepository;

    public ShoppingService(ShoppingItemRepository shoppingItemRepository,
                           CategoryBalanceRepository categoryBalanceRepository,
                           CategoryRepository categoryRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
        this.categoryBalanceRepository = categoryBalanceRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ShoppingItemResponse> list(Long userId, ShoppingListType listType) {
        List<ShoppingItem> items = listType == null
                ? shoppingItemRepository.findByUserId(userId)
                : shoppingItemRepository.findByUserIdAndListType(userId, listType);

        Comparator<ShoppingItem> comparator = resolveComparator(listType);

        return items.stream()
                .sorted(comparator)
                .map(item -> buildResponse(item, userId))
                .toList();
    }

    @Transactional
    public ShoppingItemResponse create(Long userId, ShoppingItemRequest req) {
        if (req.envelopeId() != null) {
            validateEnvelopeOwnership(req.envelopeId(), userId);
        }

        ShoppingItem item = new ShoppingItem(
                userId,
                req.listType(),
                req.name(),
                req.estimatedPrice(),
                req.envelopeId(),
                req.priority(),
                req.notes()
        );

        return buildResponse(shoppingItemRepository.save(item), userId);
    }

    @Transactional
    public ShoppingItemResponse update(Long userId, Long id, ShoppingItemRequest req) {
        ShoppingItem item = findOwned(userId, id);

        // listType is immutable after creation: reject a mismatch instead of
        // silently ignoring it, so the client never thinks the change applied.
        if (req.listType() != item.getListType()) {
            throw new InvalidShoppingException("listType cannot be changed after creation");
        }

        if (req.envelopeId() != null) {
            validateEnvelopeOwnership(req.envelopeId(), userId);
        }

        item.setName(req.name());
        item.setEstimatedPrice(req.estimatedPrice());
        item.setEnvelopeId(req.envelopeId());
        item.setPriority(req.priority());
        item.setNotes(req.notes());
        // listType is intentionally immutable after creation

        return buildResponse(item, userId);
    }

    @Transactional
    public ShoppingItemResponse setBought(Long userId, Long id, boolean bought) {
        ShoppingItem item = findOwned(userId, id);
        item.setBought(bought);
        return buildResponse(item, userId);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        shoppingItemRepository.delete(findOwned(userId, id));
    }

    private ShoppingItem findOwned(Long userId, Long id) {
        // 404 (not 403): do not reveal whether the item belongs to another user
        return shoppingItemRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Shopping item not found"));
    }

    private void validateEnvelopeOwnership(Long categoryId, Long userId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Envelope not found"));
        if (category.getType() != TransactionType.EXPENSE) {
            throw new InvalidShoppingException("Envelope must be an expense category");
        }
    }

    private ShoppingItemResponse buildResponse(ShoppingItem item, Long userId) {
        if (item.getEnvelopeId() == null) {
            return new ShoppingItemResponse(
                    item.getId(),
                    item.getListType(),
                    item.getName(),
                    item.getEstimatedPrice(),
                    null,
                    null,
                    null,
                    item.getPriority(),
                    item.isBought(),
                    null,
                    item.getNotes(),
                    item.getCreatedAt()
            );
        }

        BigDecimal envelopeBalance = categoryBalanceRepository
                .findByCategoryId(item.getEnvelopeId())
                .filter(cb -> cb.getUserId().equals(userId))
                .map(CategoryBalance::getBalance)
                .orElse(null);

        // Category may have been deleted after the item was created; use null gracefully
        String envelopeName = categoryRepository
                .findByIdAndUserId(item.getEnvelopeId(), userId)
                .map(Category::getName)
                .orElse(null);

        Boolean feasible = null;
        if (item.getListType() == ShoppingListType.WISHLIST
                && item.getEstimatedPrice() != null
                && envelopeBalance != null) {
            feasible = envelopeBalance.compareTo(item.getEstimatedPrice()) >= 0;
        }

        return new ShoppingItemResponse(
                item.getId(),
                item.getListType(),
                item.getName(),
                item.getEstimatedPrice(),
                item.getEnvelopeId(),
                envelopeName,
                envelopeBalance,
                item.getPriority(),
                item.isBought(),
                feasible,
                item.getNotes(),
                item.getCreatedAt()
        );
    }

    private static Comparator<ShoppingItem> resolveComparator(ShoppingListType listType) {
        if (listType == ShoppingListType.GROCERY) {
            // Pending items first (bought=false), then alphabetical
            return Comparator.comparing(ShoppingItem::isBought)
                    .thenComparing(ShoppingItem::getName, String.CASE_INSENSITIVE_ORDER);
        }
        // WISHLIST and mixed (null): priority asc nulls last, then alphabetical
        return Comparator.comparing(ShoppingItem::getPriority,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ShoppingItem::getName, String.CASE_INSENSITIVE_ORDER);
    }
}
