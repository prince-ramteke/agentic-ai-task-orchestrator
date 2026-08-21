package com.prince.agentic.tool.math;

import com.prince.agentic.security.RoleNames;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import com.prince.agentic.tool.exception.ToolInvalidInputException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * {@code math.calculate} — deterministic arithmetic over a safe grammar (see {@link ExpressionEvaluator}).
 * No I/O, no side effects, no code execution. Demonstrates that the tool framework is not tied to
 * database CRUD.
 */
@Component
public class CalculatorTool implements Tool<CalculatorInput, CalculationResult> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "math.calculate",
            "Evaluate a basic arithmetic expression (+, -, *, /, parentheses, decimals).",
            "math", "1", ToolRiskLevel.DETERMINISTIC, true, Set.of(RoleNames.ROLE_USER, RoleNames.ROLE_ADMIN),
            CalculatorInput.class, CalculationResult.class, Duration.ofSeconds(10));

    private final ExpressionEvaluator evaluator;

    public CalculatorTool(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CalculationResult execute(ToolExecutionContext context, CalculatorInput input) {
        try {
            return new CalculationResult(input.expression(), evaluator.evaluate(input.expression()));
        } catch (IllegalArgumentException e) {
            throw new ToolInvalidInputException("Invalid expression: " + e.getMessage());
        }
    }
}
