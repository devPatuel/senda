package dev.jordi.senda.imports;

import dev.jordi.senda.category.Category;
import dev.jordi.senda.category.CategoryRepository;
import dev.jordi.senda.common.NotFoundException;
import dev.jordi.senda.common.TransactionType;
import dev.jordi.senda.space.SpaceAccess;
import dev.jordi.senda.transaction.Transaction;
import dev.jordi.senda.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ImportService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final SpaceAccess spaceAccess;

    public ImportService(TransactionRepository transactionRepository,
                         CategoryRepository categoryRepository,
                         SpaceAccess spaceAccess) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.spaceAccess = spaceAccess;
    }

    /**
     * Normalizes each parsed row (sign → type, absolute amount) and flags rows
     * that duplicate an existing transaction by (date, amount, description).
     */
    @Transactional(readOnly = true)
    public List<ImportPreviewRow> preview(Long userId, ImportPreviewRequest request) {
        Long spaceId = request.spaceId();
        if (spaceId != null) {
            spaceAccess.assertActiveMember(userId, spaceId);
        }
        return request.rows().stream().map(in -> {
            TransactionType type = in.amount().signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
            BigDecimal amount = in.amount().abs().setScale(2, RoundingMode.HALF_UP);

            boolean duplicate = spaceId == null
                    ? transactionRepository.existsByUserIdAndDateAndAmountAndDescriptionAndSpaceIdIsNull(
                            userId, in.date(), amount, in.description())
                    : transactionRepository.existsBySpaceIdAndDateAndAmountAndDescription(
                            spaceId, in.date(), amount, in.description());
            return new ImportPreviewRow(in.date(), in.description(), amount, type, duplicate);
        }).toList();
    }

    /**
     * Creates a transaction for each confirmed row, skipping any that already
     * exist (re-checked here so a stale preview cannot create duplicates).
     */
    @Transactional
    public ImportCommitResponse commit(Long userId, ImportCommitRequest request) {
        Long spaceId = request.spaceId();
        if (spaceId != null) {
            spaceAccess.assertActiveMember(userId, spaceId);
        }
        int imported = 0;
        int skipped = 0;
        for (ImportCommitRequest.Row row : request.rows()) {
            // Resolving in the target scope is what enforces it: a category from the
            // other scope is simply not found -> 404.
            Category category = (spaceId == null
                    ? categoryRepository.findByIdAndUserIdAndSpaceIdIsNull(row.categoryId(), userId)
                    : categoryRepository.findByIdAndSpaceId(row.categoryId(), spaceId))
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
            boolean exists = spaceId == null
                    ? transactionRepository.existsByUserIdAndDateAndAmountAndDescriptionAndSpaceIdIsNull(
                            userId, row.date(), row.amount(), row.description())
                    : transactionRepository.existsBySpaceIdAndDateAndAmountAndDescription(
                            spaceId, row.date(), row.amount(), row.description());
            if (exists) {
                skipped++;
                continue;
            }
            Transaction transaction = new Transaction(
                    userId, category, row.type(), row.amount(), row.date(), row.description());
            transaction.setSpaceId(spaceId);
            transactionRepository.save(transaction);
            imported++;
        }
        return new ImportCommitResponse(imported, skipped);
    }
}
