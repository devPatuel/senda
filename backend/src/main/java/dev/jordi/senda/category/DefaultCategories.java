package dev.jordi.senda.category;

import dev.jordi.senda.common.TransactionType;

import java.util.List;

/**
 * Default category set copied to every user on registration.
 */
public final class DefaultCategories {

    public record Definition(String name, TransactionType type, String color) {
    }

    public static final List<Definition> ALL = List.of(
            new Definition("Comida", TransactionType.EXPENSE, "#EF4444"),
            new Definition("Transporte", TransactionType.EXPENSE, "#3B82F6"),
            new Definition("Vivienda", TransactionType.EXPENSE, "#8B5CF6"),
            new Definition("Ocio", TransactionType.EXPENSE, "#F59E0B"),
            new Definition("Salud", TransactionType.EXPENSE, "#10B981"),
            new Definition("Compras", TransactionType.EXPENSE, "#EC4899"),
            new Definition("Otros gastos", TransactionType.EXPENSE, "#6B7280"),
            new Definition("Nómina", TransactionType.INCOME, "#22C55E"),
            new Definition("Otros ingresos", TransactionType.INCOME, "#14B8A6"));

    private DefaultCategories() {
    }
}
