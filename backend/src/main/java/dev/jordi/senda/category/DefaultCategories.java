package dev.jordi.senda.category;

import dev.jordi.senda.common.TransactionType;

import java.util.List;

/**
 * Default category set copied to every user on registration.
 */
public final class DefaultCategories {

    public record Definition(String name, TransactionType type, String color, boolean fixed) {
    }

    public static final List<Definition> ALL = List.of(
            new Definition("Comida", TransactionType.EXPENSE, "#EF4444", false),
            new Definition("Transporte", TransactionType.EXPENSE, "#3B82F6", false),
            new Definition("Vivienda", TransactionType.EXPENSE, "#8B5CF6", true),
            new Definition("Ocio", TransactionType.EXPENSE, "#F59E0B", false),
            new Definition("Salud", TransactionType.EXPENSE, "#10B981", false),
            new Definition("Compras", TransactionType.EXPENSE, "#EC4899", false),
            new Definition("Otros gastos", TransactionType.EXPENSE, "#6B7280", false),
            new Definition("Nómina", TransactionType.INCOME, "#22C55E", false),
            new Definition("Otros ingresos", TransactionType.INCOME, "#14B8A6", false));

    private DefaultCategories() {
    }
}
