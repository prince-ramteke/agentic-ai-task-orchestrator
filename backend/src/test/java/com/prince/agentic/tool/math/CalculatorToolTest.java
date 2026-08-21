package com.prince.agentic.tool.math;

import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.AbstractToolContractTest;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import com.prince.agentic.tool.exception.ToolInvalidInputException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculatorToolTest extends AbstractToolContractTest {

    private final CalculatorTool calculator = new CalculatorTool(new ExpressionEvaluator());

    @Override
    protected Tool<?, ?> tool() {
        return calculator;
    }

    private ToolExecutionContext ctx() {
        return ToolExecutionContext.forPrincipal(new AuthenticatedUser(1L, "u@x.com", Set.of("ROLE_USER")));
    }

    @Test
    void descriptor_is_deterministic_math_calculate() {
        assertThat(calculator.descriptor().name()).isEqualTo("math.calculate");
        assertThat(calculator.descriptor().risk()).isEqualTo(ToolRiskLevel.DETERMINISTIC);
    }

    @Test
    void execute_evaluates_expression() {
        CalculationResult r = calculator.execute(ctx(), new CalculatorInput("2 + 3 * 4"));
        assertThat(r.result()).isEqualByComparingTo(new BigDecimal("14"));
        assertThat(r.expression()).isEqualTo("2 + 3 * 4");
    }

    @Test
    void execute_maps_bad_expression_to_invalid_input() {
        assertThatThrownBy(() -> calculator.execute(ctx(), new CalculatorInput("2 + )")))
                .isInstanceOf(ToolInvalidInputException.class);
    }
}
