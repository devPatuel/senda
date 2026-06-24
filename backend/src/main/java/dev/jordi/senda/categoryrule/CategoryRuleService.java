package dev.jordi.senda.categoryrule;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.ConflictException;
import dev.jordi.senda.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CategoryRuleService {

    private final CategoryRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;

    public CategoryRuleService(CategoryRuleRepository ruleRepository,
                               CategoryRepository categoryRepository) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryRuleResponse> list(Long userId) {
        Map<Long, Category> categories = categoryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        return ruleRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(CategoryRule::getMatchText, String.CASE_INSENSITIVE_ORDER))
                .map(r -> toResponse(r, categories.get(r.getCategoryId())))
                .toList();
    }

    @Transactional
    public CategoryRuleResponse create(Long userId, CategoryRuleRequest request) {
        Category category = requireOwnedCategory(userId, request.categoryId());
        String matchText = request.matchText().trim();
        if (ruleRepository.existsByUserIdAndMatchText(userId, matchText)) {
            throw new ConflictException("A rule with this text already exists");
        }
        CategoryRule saved = ruleRepository.save(new CategoryRule(userId, matchText, category.getId()));
        return toResponse(saved, category);
    }

    @Transactional
    public CategoryRuleResponse update(Long userId, Long id, CategoryRuleRequest request) {
        CategoryRule rule = findOwned(userId, id);
        Category category = requireOwnedCategory(userId, request.categoryId());
        String matchText = request.matchText().trim();
        if (!rule.getMatchText().equalsIgnoreCase(matchText)
                && ruleRepository.existsByUserIdAndMatchText(userId, matchText)) {
            throw new ConflictException("A rule with this text already exists");
        }
        rule.setMatchText(matchText);
        rule.setCategoryId(category.getId());
        return toResponse(rule, category);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ruleRepository.delete(findOwned(userId, id));
    }

    /**
     * First rule whose {@code matchText} is contained in {@code description}
     * (case-insensitive). Used by the CSV import to pre-fill categories.
     */
    public static Optional<CategoryRule> firstMatch(List<CategoryRule> rules, String description) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String haystack = description.toLowerCase(Locale.ROOT);
        return rules.stream()
                .filter(r -> haystack.contains(r.getMatchText().toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    private Category requireOwnedCategory(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private CategoryRule findOwned(Long userId, Long id) {
        return ruleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Rule not found"));
    }

    private static CategoryRuleResponse toResponse(CategoryRule rule, Category category) {
        return new CategoryRuleResponse(
                rule.getId(),
                rule.getMatchText(),
                rule.getCategoryId(),
                category != null ? category.getName() : null,
                category != null ? category.getColor() : null);
    }
}
