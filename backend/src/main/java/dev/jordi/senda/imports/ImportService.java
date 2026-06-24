package dev.jordi.senda.imports;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.categoryrule.CategoryRule;
import dev.jordi.senda.categoryrule.CategoryRuleRepository;
import dev.jordi.senda.categoryrule.CategoryRuleService;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.transaction.Transaction;
import dev.jordi.senda.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ImportService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryRuleRepository ruleRepository;

    public ImportService(TransactionRepository transactionRepository,
                         CategoryRepository categoryRepository,
                         CategoryRuleRepository ruleRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
    }

    /**
     * Normalizes each parsed row (sign → type, absolute amount), suggests a
     * category from the matching rule, and flags rows that duplicate an existing
     * transaction by (date, amount, description).
     */
    @Transactional(readOnly = true)
    public List<ImportPreviewRow> preview(Long userId, ImportPreviewRequest request) {
        List<CategoryRule> rules = ruleRepository.findByUserId(userId);
        Map<Long, Category> categories = categoryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        return request.rows().stream().map(in -> {
            TransactionType type = in.amount().signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
            BigDecimal amount = in.amount().abs().setScale(2, RoundingMode.HALF_UP);

            Long suggestedId = null;
            String suggestedName = null;
            Optional<CategoryRule> match = CategoryRuleService.firstMatch(rules, in.description());
            if (match.isPresent()) {
                Category c = categories.get(match.get().getCategoryId());
                // Only suggest when the rule's category type matches the row's sign,
                // since a transaction's type must equal its category's type.
                if (c != null && c.isActive() && c.getType() == type) {
                    suggestedId = c.getId();
                    suggestedName = c.getName();
                }
            }

            boolean duplicate = transactionRepository.existsByUserIdAndDateAndAmountAndDescription(
                    userId, in.date(), amount, in.description());
            return new ImportPreviewRow(in.date(), in.description(), amount, type,
                    suggestedId, suggestedName, duplicate);
        }).toList();
    }

    /**
     * Creates a transaction for each confirmed row, skipping any that already
     * exist (re-checked here so a stale preview cannot create duplicates).
     */
    @Transactional
    public ImportCommitResponse commit(Long userId, ImportCommitRequest request) {
        int imported = 0;
        int skipped = 0;
        for (ImportCommitRequest.Row row : request.rows()) {
            Category category = categoryRepository.findByIdAndUserId(row.categoryId(), userId)
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            if (!category.isActive()) {
                throw new InvalidImportException("Category is inactive: " + category.getName());
            }
            if (category.getType() != row.type()) {
                throw new InvalidImportException("Transaction type " + row.type()
                        + " does not match category type " + category.getType());
            }
            // The DB check sees rows flushed earlier in this batch, so duplicate
            // CSV lines collapse to a single transaction too.
            if (transactionRepository.existsByUserIdAndDateAndAmountAndDescription(
                    userId, row.date(), row.amount(), row.description())) {
                skipped++;
                continue;
            }
            transactionRepository.save(new Transaction(
                    userId, category, row.type(), row.amount(), row.date(), row.description()));
            imported++;
        }
        return new ImportCommitResponse(imported, skipped);
    }
}
