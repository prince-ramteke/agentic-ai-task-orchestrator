package com.prince.agentic.tool.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The safe evaluator: correct arithmetic, and rejection of anything outside the grammar. */
class ExpressionEvaluatorTest {

    private final ExpressionEvaluator eval = new ExpressionEvaluator();

    private void assertEval(String expr, String expected) {
        assertThat(eval.evaluate(expr)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test void addition() { assertEval("2 + 3", "5"); }
    @Test void precedence() { assertEval("2 + 3 * 4", "14"); }
    @Test void parentheses() { assertEval("(2 + 3) * 4", "20"); }
    @Test void decimals() { assertEval("1.5 * 2", "3.0"); }
    @Test void unary_minus() { assertEval("-3 + 5", "2"); }
    @Test void nested() { assertEval("((1+2) * (3+4)) - 1", "20"); }
    @Test void division() { assertEval("10 / 4", "2.5"); }

    @Test void divide_by_zero_rejected() {
        assertThatThrownBy(() -> eval.evaluate("1 / 0")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void letters_rejected() {
        assertThatThrownBy(() -> eval.evaluate("2 + a")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void code_like_input_rejected() {
        assertThatThrownBy(() -> eval.evaluate("System.exit(0)")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void semicolon_rejected() {
        assertThatThrownBy(() -> eval.evaluate("1;2")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void trailing_operator_rejected() {
        assertThatThrownBy(() -> eval.evaluate("2 +")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unbalanced_parenthesis_rejected() {
        assertThatThrownBy(() -> eval.evaluate("(2 + 3")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void empty_rejected() {
        assertThatThrownBy(() -> eval.evaluate("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
