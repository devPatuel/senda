package dev.jordi.senda.wishlist;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class WishlistService {

    private final WishlistItemRepository repository;
    private final WishListRepository listRepository;

    public WishlistService(WishlistItemRepository repository, WishListRepository listRepository) {
        this.repository = repository;
        this.listRepository = listRepository;
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
        List<WishListSummary> lists = listRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(list -> summarize(list, all))
                .toList();
        BigDecimal unlistedTotal = sumPrices(all.stream().filter(i -> i.getListId() == null).toList());
        return new WishlistResponse(items, sumPrices(all), lists, unlistedTotal);
    }

    @Transactional
    public WishListSummary createList(Long userId, WishListRequest req) {
        String name = req.name().trim();
        if (listRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("A wish list with that name already exists");
        }
        WishList saved = listRepository.save(new WishList(userId, name));
        return new WishListSummary(saved.getId(), name, 0, BigDecimal.ZERO);
    }

    @Transactional
    public WishListSummary renameList(Long userId, Long id, WishListRequest req) {
        WishList list = findOwnedList(userId, id);
        String name = req.name().trim();
        if (listRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
            throw new ConflictException("A wish list with that name already exists");
        }
        list.setName(name);
        return summarize(list, repository.findByUserId(userId));
    }

    /** The wishes stay: the foreign key leaves them without a list (ON DELETE SET NULL). */
    @Transactional
    public void deleteList(Long userId, Long id) {
        listRepository.delete(findOwnedList(userId, id));
    }

    private static WishListSummary summarize(WishList list, List<WishlistItem> all) {
        List<WishlistItem> inList = all.stream().filter(i -> list.getId().equals(i.getListId())).toList();
        return new WishListSummary(list.getId(), list.getName(), inList.size(), sumPrices(inList));
    }

    private static BigDecimal sumPrices(List<WishlistItem> items) {
        return items.stream()
                .map(WishlistItem::getPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private WishList findOwnedList(Long userId, Long id) {
        return listRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Wish list not found"));
    }

    // Without this check a wish could point at a list of another user.
    private Long ownedListId(Long userId, Long listId) {
        return listId == null ? null : findOwnedList(userId, listId).getId();
    }

    @Transactional
    public WishlistItemResponse create(Long userId, WishlistItemRequest req) {
        WishlistItem item = new WishlistItem(userId, req.name(), blankToNull(req.imageUrl()),
                blankToNull(req.productUrl()), blankToNull(req.comment()), req.price(), req.priority());
        item.setListId(ownedListId(userId, req.listId()));
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
        item.setListId(ownedListId(userId, req.listId()));
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
