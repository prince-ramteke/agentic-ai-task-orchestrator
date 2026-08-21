package com.prince.agentic.tool.math;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Safe arithmetic evaluator — a small recursive-descent parser over {@code BigDecimal}.
 *
 * <p><b>No</b> {@code ScriptEngine}, {@code eval}, {@code Runtime.exec}, {@code ProcessBuilder}, or
 * reflection: it can never execute code. Anything outside the grammar is rejected with
 * {@link IllegalArgumentException} (the {@code CalculatorTool} maps that to {@code TOOL_INVALID_INPUT}).
 *
 * <p>Grammar (whitespace allowed between tokens):
 * <pre>
 *   expr   = term   (('+' | '-') term)*
 *   term   = factor (('*' | '/') factor)*
 *   factor = number | '(' expr ')' | '-' factor
 *   number = digits ['.' digits]
 * </pre>
 * No functions, variables, or exponent — intentionally minimal for M5.
 */
@Component
public class ExpressionEvaluator {

    private String s;
    private int pos;

    /** Evaluate an expression. {@code synchronized} because the parser holds cursor state on the instance. */
    public synchronized BigDecimal evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("empty expression");
        }
        this.s = expression;
        this.pos = 0;
        BigDecimal value = parseExpr();
        skipWs();
        if (pos != s.length()) {
            throw new IllegalArgumentException("unexpected token at position " + pos);
        }
        return value;
    }

    private BigDecimal parseExpr() {
        BigDecimal v = parseTerm();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '+') {
                pos++;
                v = v.add(parseTerm());
            } else if (c == '-') {
                pos++;
                v = v.subtract(parseTerm());
            } else {
                return v;
            }
        }
    }

    private BigDecimal parseTerm() {
        BigDecimal v = parseFactor();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '*') {
                pos++;
                v = v.multiply(parseFactor());
            } else if (c == '/') {
                pos++;
                BigDecimal divisor = parseFactor();
                if (divisor.signum() == 0) {
                    throw new IllegalArgumentException("division by zero");
                }
                v = v.divide(divisor, MathContext.DECIMAL64);
            } else {
                return v;
            }
        }
    }

    private BigDecimal parseFactor() {
        skipWs();
        char c = peek();
        if (c == '-') {
            pos++;
            return parseFactor().negate();
        }
        if (c == '(') {
            pos++;
            BigDecimal v = parseExpr();
            skipWs();
            if (peek() != ')') {
                throw new IllegalArgumentException("expected ')' at position " + pos);
            }
            pos++;
            return v;
        }
        return parseNumber();
    }

    private BigDecimal parseNumber() {
        skipWs();
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
            pos++;
        }
        if (pos == start) {
            throw new IllegalArgumentException("expected a number at position " + start);
        }
        try {
            return new BigDecimal(s.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid number: " + s.substring(start, pos));
        }
    }

    private char peek() {
        return pos < s.length() ? s.charAt(pos) : '\0';
    }

    private void skipWs() {
        while (pos < s.length() && s.charAt(pos) == ' ') {
            pos++;
        }
    }
}
