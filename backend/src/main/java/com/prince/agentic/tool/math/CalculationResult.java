package com.prince.agentic.tool.math;

import java.math.BigDecimal;

/** Output for {@code math.calculate}: the original expression and its evaluated result. */
public record CalculationResult(String expression, BigDecimal result) {}
