package dev.jordi.senda.category;

import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId, TransactionType type, boolean includeInactive) {
        List<Category> categories = includeInactive
                ? categoryRepository.findByUserId(userId)
                : categoryRepository.findByUserIdAndActiveTrue(userId);
        return categories.stream()
                .filter(category -> type == null || category.getType() == type)
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Category::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        if (categoryRepository.existsByUserIdAndNameAndType(userId, request.name(), request.type())) {
            throw new ConflictException("Category already exists for this type");
        }
        Category saved = categoryRepository.save(
                new Category(userId, request.name(), request.type(), request.color()));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long userId, Long id, CategoryUpdateRequest request) {
        Category category = findOwned(userId, id);
        boolean renamed = !category.getName().equals(request.name());
        if (renamed && categoryRepository.existsByUserIdAndNameAndType(
                userId, request.name(), category.getType())) {
            throw new ConflictException("Category already exists for this type");
        }
        category.setName(request.name());
        category.setColor(request.color());
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category category = findOwned(userId, id);
        // A category with transactions is never removed: deactivate to keep history intact
        if (transactionRepository.existsByCategoryId(category.getId())) {
            category.setActive(false);
            categoryRepository.save(category);
        } else {
            categoryRepository.delete(category);
        }
    }

    private Category findOwned(Long userId, Long id) {
        // 404 (not 403) for another user's category: do not reveal its existence
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }
}
