package dev.jordi.senda.transaction;

import dev.jordi.senda.common.TransactionType;

import java.math.BigDecimal;

/**
 * Aggregated total for one (year, month, type) bucket, built by a JPQL
 * constructor expression. The trends view reshapes these into per-month rows.
 */
public record MonthlyTotal(Integer year, Integer month, TransactionType type, BigDecimal total) {
}
