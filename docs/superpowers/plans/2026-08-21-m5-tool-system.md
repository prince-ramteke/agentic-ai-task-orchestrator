# Milestone 5 — Tool Registry & Tool Execution Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the deterministic tool infrastructure the future M6 agent will use — a typed, validated, authorized execution boundary (`Tool<I,O>`, `ToolDescriptor`, `ToolRegistry`, `ToolExecutor`, `ToolResult<O>`) plus six least-privilege tools — with **no** agent, LLM tool-calling, Redis, guardrails, or durable audit.

**Architecture:** `(future agent) → ToolExecutor.execute(name, rawArgs, ctx) → resolve(ToolRegistry) → role-authorize → bind+validate input → Tool.execute(ctx, I) → TaskService/CustomerService → AuthorizationService → Repository`. Identity comes from the authenticated principal in `ToolExecutionContext` (never from arguments). Domain tools reuse the M3 services (ownership/404-masking/admin-any-by-id inherited). The whole subsystem imports **no** Spring AI / `ai.*` (enforced by a boundary test).

**Tech Stack:** Java 21 · Spring Boot 3.4.1 · Spring Web · Spring Security 6 (existing) · Bean Validation · Jackson (arg binding) · Micrometer (existing) · JUnit 5 · Mockito · Maven. **No new dependencies.**

**Spec:** `docs/superpowers/specs/2026-08-21-m5-tool-system-design.md` (read alongside; tables **D1–D25** and reconciliations **R1–R3** are authoritative).

## Global Constraints

- **Package:** `com.prince.agentic.tool` (+ `tool.exception`, `tool.math`, `tool.task`, `tool.customer`, `tool.api`). Package-by-feature.
- **No Spring AI / `ai.*` imports anywhere under `tool`.** No `EntityManager`, `JdbcTemplate`, or `*Repository` imports — domain tools call `TaskService`/`CustomerService` only.
- **Identity is backend-supplied.** `ToolExecutionContext` is built from an `AuthenticatedUser`; tool inputs contain **no** identity field. Reject unknown JSON properties on binding (so a spoofed `ownerId`/`userId` → `TOOL_INVALID_INPUT`, loud not silent).
- **Two authorization layers:** role/tool-type in `ToolExecutor` (from `descriptor.requiredRoles()`); resource/ownership inside the domain service (via the passed principal). Never collapse or re-implement ownership.
- **Fail closed:** unknown tool → `TOOL_NOT_FOUND`; missing auth → `TOOL_UNAUTHORIZED`; missing role → `TOOL_FORBIDDEN`; bad/oversized input → `TOOL_INVALID_INPUT`; all **before** execution.
- **Risk levels:** `READ_ONLY, DETERMINISTIC, SIDE_EFFECTING, HIGH_RISK` (aligned to `TOOL_SYSTEM.md`; **no `LOW_RISK`**). Tool names are **dot-namespaced** (`task.get`), never Java class names.
- **Errors** extend `com.prince.agentic.common.exception.ApiException`; codes `TOOL_NOT_FOUND`(404) `TOOL_INVALID_INPUT`(400) `TOOL_UNAUTHORIZED`(401) `TOOL_FORBIDDEN`(403) `TOOL_TIMEOUT`(504, reserved) `TOOL_EXECUTION_FAILED`(500) `TOOL_REGISTRATION_ERROR`(startup). Domain `ApiException` codes pass through unchanged. No stack traces / class names leaked.
- **Executor returns `ToolResult<O>`** for every execution-path outcome (never throws for those); `ToolRegistrationException` is the only exception that fails boot.
- **Timeout is metadata + measured `durationMs` only** (default 10s per tool). **No** hard interruption in M5 (M8).
- **No new dependencies, no Flyway migration, no DB tables.** Micrometer `tool.execution.duration`/`tool.execution.result` (tags `tool`/`risk`/`outcome`). Never log tool arguments in full.
- **Coverage:** keep JaCoCo `BUNDLE ≥ 0.75`; add exclude `com/prince/agentic/tool/api/**`. No coverage-padding tests.
- **Do NOT commit or push** — the human integrates. Each `git commit` step is a **checkpoint marker**.

---

## File Structure

**New production files**
```
backend/src/main/java/com/prince/agentic/tool/Tool.java
backend/src/main/java/com/prince/agentic/tool/ToolRiskLevel.java
backend/src/main/java/com/prince/agentic/tool/ToolDescriptor.java
backend/src/main/java/com/prince/agentic/tool/ToolExecutionContext.java
backend/src/main/java/com/prince/agentic/tool/ToolError.java
backend/src/main/java/com/prince/agentic/tool/ToolResult.java
backend/src/main/java/com/prince/agentic/tool/ToolRegistry.java
backend/src/main/java/com/prince/agentic/tool/ToolExecutor.java
backend/src/main/java/com/prince/agentic/tool/exception/ToolException.java
backend/src/main/java/com/prince/agentic/tool/exception/ToolNotFoundException.java
backend/src/main/java/com/prince/agentic/tool/exception/ToolInvalidInputException.java
backend/src/main/java/com/prince/agentic/tool/exception/ToolUnauthorizedException.java
backend/src/main/java/com/prince/agentic/tool/exception/ToolForbiddenException.java
backend/src/main/java/com/prince/agentic/tool/exception/ToolTimeoutException.java
backend/src/main/java/com/prince/agentic/tool/exception/ToolExecutionFailedException.java
backend/src/main/java/com/prince/agentic/tool/exception/ToolRegistrationException.java
backend/src/main/java/com/prince/agentic/tool/math/CalculatorInput.java
backend/src/main/java/com/prince/agentic/tool/math/CalculationResult.java
backend/src/main/java/com/prince/agentic/tool/math/ExpressionEvaluator.java
backend/src/main/java/com/prince/agentic/tool/math/CalculatorTool.java
backend/src/main/java/com/prince/agentic/tool/task/TaskGetInput.java
backend/src/main/java/com/prince/agentic/tool/task/TaskSearchInput.java
backend/src/main/java/com/prince/agentic/tool/task/TaskGetTool.java
backend/src/main/java/com/prince/agentic/tool/task/TaskSearchTool.java
backend/src/main/java/com/prince/agentic/tool/task/TaskCreateTool.java
backend/src/main/java/com/prince/agentic/tool/customer/CustomerGetInput.java
backend/src/main/java/com/prince/agentic/tool/customer/CustomerSearchInput.java
backend/src/main/java/com/prince/agentic/tool/customer/CustomerGetTool.java
backend/src/main/java/com/prince/agentic/tool/customer/CustomerSearchTool.java
backend/src/main/java/com/prince/agentic/tool/api/dto/ToolDescriptorResponse.java
backend/src/main/java/com/prince/agentic/tool/api/ToolCatalogController.java
```

**New test files**
```
backend/src/test/java/com/prince/agentic/tool/ToolDescriptorTest.java
backend/src/test/java/com/prince/agentic/tool/ToolRegistryTest.java
backend/src/test/java/com/prince/agentic/tool/ToolExecutorTest.java
backend/src/test/java/com/prince/agentic/tool/AbstractToolContractTest.java
backend/src/test/java/com/prince/agentic/tool/math/ExpressionEvaluatorTest.java
backend/src/test/java/com/prince/agentic/tool/math/CalculatorToolTest.java
backend/src/test/java/com/prince/agentic/tool/task/TaskGetToolTest.java
backend/src/test/java/com/prince/agentic/tool/task/TaskSearchToolTest.java
backend/src/test/java/com/prince/agentic/tool/task/TaskCreateToolTest.java
backend/src/test/java/com/prince/agentic/tool/customer/CustomerGetToolTest.java
backend/src/test/java/com/prince/agentic/tool/customer/CustomerSearchToolTest.java
backend/src/test/java/com/prince/agentic/tool/ToolSecurityTest.java
backend/src/test/java/com/prince/agentic/tool/ToolCatalogApiTest.java
backend/src/test/java/com/prince/agentic/tool/ToolArchitectureBoundaryTest.java
```

**Modified files**
```
backend/pom.xml                     # JaCoCo excludes += com/prince/agentic/tool/api/**
docs/{TOOL_SYSTEM,AGENT_ARCHITECTURE,SECURITY,GUARDRAILS,EVALUATION,OBSERVABILITY,API,TESTING,PERFORMANCE,ROADMAP,TECH_STACK,CHANGELOG}.md
docs/ADR/README.md ; README.md ; backend/README.md
```
**New docs**
```
docs/ADR/0011-tool-abstraction-and-registry.md
docs/ADR/0012-tool-authorization-and-execution-context.md
```

---

### Task 1: Core value types — risk, descriptor, context, error, result

**Files:**
- Create: `tool/ToolRiskLevel.java`, `tool/Tool.java`, `tool/ToolDescriptor.java`, `tool/ToolExecutionContext.java`, `tool/ToolError.java`, `tool/ToolResult.java`
- Test: `tool/ToolDescriptorTest.java`

**Interfaces:**
- Consumes: `com.prince.agentic.security.AuthenticatedUser`.
- Produces: `Tool<I,O>` (`ToolDescriptor descriptor()`, `O execute(ToolExecutionContext, I)`); `ToolDescriptor` record (compact-ctor validation); `ToolExecutionContext.forPrincipal(AuthenticatedUser)`; `ToolResult.ok(name,data,ms)` / `ToolResult.failure(name,ToolError,ms)`; `ToolError(code,message)`; `ToolRiskLevel{READ_ONLY,DETERMINISTIC,SIDE_EFFECTING,HIGH_RISK}`.

- [ ] **Step 1: Write the failing test** (`ToolDescriptorTest.java`):
```java
package com.prince.agentic.tool;

import com.prince.agentic.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolDescriptorTest {

    private ToolDescriptor valid() {
        return new ToolDescriptor("task.get", "Get one task by id", "task", "1",
                ToolRiskLevel.READ_ONLY, true, Set.of("ROLE_USER"),
                String.class, String.class, Duration.ofSeconds(10));
    }

    @Test
    void descriptor_holds_metadata() {
        ToolDescriptor d = valid();
        assertThat(d.name()).isEqualTo("task.get");
        assertThat(d.risk()).isEqualTo(ToolRiskLevel.READ_ONLY);
        assertThat(d.requiresAuthentication()).isTrue();
    }

    @Test
    void descriptor_rejects_blank_name() {
        assertThatThrownBy(() -> new ToolDescriptor(" ", "d", "c", "1", ToolRiskLevel.READ_ONLY,
                true, Set.of(), String.class, String.class, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptor_rejects_null_risk_and_types() {
        assertThatThrownBy(() -> new ToolDescriptor("n", "d", "c", "1", null,
                true, Set.of(), String.class, String.class, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ToolDescriptor("n", "d", "c", "1", ToolRiskLevel.READ_ONLY,
                true, Set.of(), null, String.class, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void context_from_principal_generates_ids_and_keeps_identity() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER"));
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user);
        assertThat(ctx.principal().userId()).isEqualTo(7L);
        assertThat(ctx.executionId()).isNotBlank();
        assertThat(ctx.requestId()).isNotBlank();
    }

    @Test
    void result_ok_and_failure_factories() {
        ToolResult<String> ok = ToolResult.ok("task.get", "data", 5);
        assertThat(ok.success()).isTrue();
        assertThat(ok.data()).isEqualTo("data");
        ToolResult<String> bad = ToolResult.failure("task.get", new ToolError("TOOL_NOT_FOUND", "no"), 1);
        assertThat(bad.success()).isFalse();
        assertThat(bad.error().code()).isEqualTo("TOOL_NOT_FOUND");
    }
}
```

- [ ] **Step 2: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=ToolDescriptorTest test
```
Expected: FAIL (types missing).

- [ ] **Step 3: Create `ToolRiskLevel`:**
```java
package com.prince.agentic.tool;

/** Side-effect/risk classification (aligned with docs/TOOL_SYSTEM.md §4). M8 guardrails use this. */
public enum ToolRiskLevel { READ_ONLY, DETERMINISTIC, SIDE_EFFECTING, HIGH_RISK }
```

- [ ] **Step 4: Create `Tool`:**
```java
package com.prince.agentic.tool;

/** A registered, permission-controlled capability. The ONLY bridge from a future agent to data/effects. */
public interface Tool<I, O> {
    ToolDescriptor descriptor();
    /** Execute over already-validated input. Return raw O; throw a typed exception on failure. */
    O execute(ToolExecutionContext context, I input);
}
```

- [ ] **Step 5: Create `ToolDescriptor`** (compact-ctor validation):
```java
package com.prince.agentic.tool;

import java.time.Duration;
import java.util.Set;

public record ToolDescriptor(
        String name, String description, String category, String version,
        ToolRiskLevel risk, boolean requiresAuthentication, Set<String> requiredRoles,
        Class<?> inputType, Class<?> outputType, Duration timeout) {

    public ToolDescriptor {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("tool description is required");
        if (risk == null) throw new IllegalArgumentException("tool risk is required");
        if (requiredRoles == null) throw new IllegalArgumentException("requiredRoles must not be null");
        if (inputType == null || outputType == null) throw new IllegalArgumentException("input/output types are required");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        requiredRoles = Set.copyOf(requiredRoles);   // defensive immutability
    }
}
```

- [ ] **Step 6: Create `ToolExecutionContext`** (identity from principal, ids generated by backend):
```java
package com.prince.agentic.tool;

import com.prince.agentic.security.AuthenticatedUser;

import java.util.Map;
import java.util.UUID;

public record ToolExecutionContext(AuthenticatedUser principal, String requestId,
                                   String executionId, Map<String, Object> metadata) {

    /** Backend-controlled construction: identity comes only from the authenticated principal. */
    public static ToolExecutionContext forPrincipal(AuthenticatedUser principal) {
        return new ToolExecutionContext(principal,
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), Map.of());
    }
}
```

- [ ] **Step 7: Create `ToolError` and `ToolResult`:**
```java
package com.prince.agentic.tool;
public record ToolError(String code, String message) {}
```
```java
package com.prince.agentic.tool;

public record ToolResult<O>(String toolName, boolean success, O data, ToolError error, long durationMs) {
    public static <O> ToolResult<O> ok(String toolName, O data, long durationMs) {
        return new ToolResult<>(toolName, true, data, null, durationMs);
    }
    public static <O> ToolResult<O> failure(String toolName, ToolError error, long durationMs) {
        return new ToolResult<>(toolName, false, null, error, durationMs);
    }
}
```

- [ ] **Step 8: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=ToolDescriptorTest test
```
Expected: PASS.

- [ ] **Step 9: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool backend/src/test/java/com/prince/agentic/tool/ToolDescriptorTest.java
git commit -m "feat: add core tool value types (descriptor, context, result) [M5]"
```

---

### Task 2: Tool exception model

**Files:**
- Create: `tool/exception/{ToolException,ToolNotFoundException,ToolInvalidInputException,ToolUnauthorizedException,ToolForbiddenException,ToolTimeoutException,ToolExecutionFailedException,ToolRegistrationException}.java`
- Test: extend `ToolDescriptorTest`? No — add `tool/exception/ToolExceptionTest.java`

**Interfaces:**
- Consumes: `com.prince.agentic.common.exception.ApiException` (`(HttpStatus, String code, String message)`).
- Produces: `ToolException extends ApiException` + concrete types with fixed status/code; `ToolRegistrationException extends RuntimeException` (startup, not an ApiException).

- [ ] **Step 1: Write the failing test** (`tool/exception/ToolExceptionTest.java`):
```java
package com.prince.agentic.tool.exception;

import com.prince.agentic.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExceptionTest {

    @Test
    void codes_and_statuses_are_stable() {
        assertThat(new ToolNotFoundException("task.x")).isInstanceOf(ApiException.class);
        assertThat(new ToolNotFoundException("task.x").getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new ToolNotFoundException("task.x").getCode()).isEqualTo("TOOL_NOT_FOUND");
        assertThat(new ToolInvalidInputException("bad").getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(new ToolInvalidInputException("bad").getCode()).isEqualTo("TOOL_INVALID_INPUT");
        assertThat(new ToolUnauthorizedException("x").getCode()).isEqualTo("TOOL_UNAUTHORIZED");
        assertThat(new ToolUnauthorizedException("x").getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(new ToolForbiddenException("x").getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(new ToolForbiddenException("x").getCode()).isEqualTo("TOOL_FORBIDDEN");
        assertThat(new ToolTimeoutException("x").getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(new ToolExecutionFailedException("x", null).getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(new ToolExecutionFailedException("x", null).getCode()).isEqualTo("TOOL_EXECUTION_FAILED");
    }
}
```

- [ ] **Step 2: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=ToolExceptionTest test
```
Expected: FAIL.

- [ ] **Step 3: Create `ToolException` base + the six `ApiException` subtypes.** Pattern (mirrors M4's `LlmException`):
```java
package com.prince.agentic.tool.exception;

import com.prince.agentic.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public abstract class ToolException extends ApiException {
    protected ToolException(HttpStatus status, String code, String message) { super(status, code, message); }
}
```
Concrete (each a `(String message)` ctor calling super with its status+code):
`ToolNotFoundException`→`(NOT_FOUND,"TOOL_NOT_FOUND")`; `ToolInvalidInputException`→`(BAD_REQUEST,"TOOL_INVALID_INPUT")`; `ToolUnauthorizedException`→`(UNAUTHORIZED,"TOOL_UNAUTHORIZED")`; `ToolForbiddenException`→`(FORBIDDEN,"TOOL_FORBIDDEN")`; `ToolTimeoutException`→`(GATEWAY_TIMEOUT,"TOOL_TIMEOUT")`. `ToolExecutionFailedException` takes `(String message, Throwable cause)` → `(INTERNAL_SERVER_ERROR,"TOOL_EXECUTION_FAILED")` then `if (cause != null) initCause(cause);`.

- [ ] **Step 4: Create `ToolRegistrationException`** (fails boot — a plain RuntimeException, NOT an ApiException):
```java
package com.prince.agentic.tool.exception;

/** Thrown at startup when the tool registry is invalid. Fails application boot (fail-fast). */
public class ToolRegistrationException extends RuntimeException {
    public ToolRegistrationException(String message) { super(message); }
}
```

- [ ] **Step 5: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=ToolExceptionTest test
```
Expected: PASS.

- [ ] **Step 6: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool/exception backend/src/test/java/com/prince/agentic/tool/exception
git commit -m "feat: add tool exception model integrated with ApiException [M5]"
```

---

### Task 3: Safe expression evaluator (calculator core)

**Files:**
- Create: `tool/math/ExpressionEvaluator.java`
- Test: `tool/math/ExpressionEvaluatorTest.java`

**Interfaces:**
- Produces: `ExpressionEvaluator.evaluate(String expr) -> BigDecimal`; throws `IllegalArgumentException` on malformed/dangerous input or divide-by-zero. Grammar: `expr = term (('+'|'-') term)*; term = factor (('*'|'/') factor)*; factor = number | '(' expr ')' | '-' factor`. Numbers are decimal (`\\d+(\\.\\d+)?`). No functions/variables/exponent.

- [ ] **Step 1: Write the failing test** (`ExpressionEvaluatorTest.java`):
```java
package com.prince.agentic.tool.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test void divide_by_zero_rejected() {
        assertThatThrownBy(() -> eval.evaluate("1 / 0")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void letters_rejected() {
        assertThatThrownBy(() -> eval.evaluate("2 + a")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void code_like_input_rejected() {
        assertThatThrownBy(() -> eval.evaluate("System.exit(0)")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void trailing_operator_rejected() {
        assertThatThrownBy(() -> eval.evaluate("2 +")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void empty_rejected() {
        assertThatThrownBy(() -> eval.evaluate("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=ExpressionEvaluatorTest test
```
Expected: FAIL.

- [ ] **Step 3: Implement `ExpressionEvaluator`** — a recursive-descent parser over a char cursor, `BigDecimal` arithmetic, `RoundingMode.HALF_UP` scale 10 for division, rejecting any character outside `[0-9. +\-*/()]` and any leftover input:
```java
package com.prince.agentic.tool.math;

import java.math.BigDecimal;
import java.math.MathContext;

/** Safe arithmetic evaluator. No ScriptEngine/eval/reflection. Grammar in the class doc. */
public class ExpressionEvaluator {

    private String s;
    private int pos;

    public synchronized BigDecimal evaluate(String expression) {
        if (expression == null || expression.isBlank()) throw new IllegalArgumentException("empty expression");
        this.s = expression;
        this.pos = 0;
        BigDecimal value = parseExpr();
        skipWs();
        if (pos != s.length()) throw new IllegalArgumentException("unexpected token at " + pos);
        return value;
    }

    private BigDecimal parseExpr() {
        BigDecimal v = parseTerm();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '+') { pos++; v = v.add(parseTerm()); }
            else if (c == '-') { pos++; v = v.subtract(parseTerm()); }
            else return v;
        }
    }

    private BigDecimal parseTerm() {
        BigDecimal v = parseFactor();
        while (true) {
            skipWs();
            char c = peek();
            if (c == '*') { pos++; v = v.multiply(parseFactor()); }
            else if (c == '/') {
                pos++;
                BigDecimal d = parseFactor();
                if (d.signum() == 0) throw new IllegalArgumentException("division by zero");
                v = v.divide(d, MathContext.DECIMAL64);
            } else return v;
        }
    }

    private BigDecimal parseFactor() {
        skipWs();
        char c = peek();
        if (c == '-') { pos++; return parseFactor().negate(); }
        if (c == '(') {
            pos++;
            BigDecimal v = parseExpr();
            skipWs();
            if (peek() != ')') throw new IllegalArgumentException("expected ')'");
            pos++;
            return v;
        }
        return parseNumber();
    }

    private BigDecimal parseNumber() {
        skipWs();
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
        if (pos == start) throw new IllegalArgumentException("expected number at " + start);
        try {
            return new BigDecimal(s.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid number: " + s.substring(start, pos));
        }
    }

    private char peek() { return pos < s.length() ? s.charAt(pos) : '\0'; }
    private void skipWs() { while (pos < s.length() && s.charAt(pos) == ' ') pos++; }
}
```
> Note: the parser rejects letters and symbols implicitly — any char that is not a number/operator/paren/space causes `parseNumber` to find no digits → `IllegalArgumentException`, or leaves leftover input → the final `pos != length` check throws. `evaluate` is `synchronized` because the parser holds cursor state; `CalculatorTool` will treat the evaluator as a shared bean.

- [ ] **Step 4: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=ExpressionEvaluatorTest test
```
Expected: PASS (all 11 cases).

- [ ] **Step 5: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool/math/ExpressionEvaluator.java backend/src/test/java/com/prince/agentic/tool/math/ExpressionEvaluatorTest.java
git commit -m "feat: add safe recursive-descent expression evaluator [M5]"
```

---

### Task 4: `math.calculate` tool + reusable contract test

**Files:**
- Create: `tool/math/CalculatorInput.java`, `tool/math/CalculationResult.java`, `tool/math/CalculatorTool.java`
- Create: `tool/AbstractToolContractTest.java`
- Test: `tool/math/CalculatorToolTest.java`

**Interfaces:**
- Consumes: `Tool`, `ToolDescriptor`, `ExpressionEvaluator`, `ToolExecutionContext`, `ToolInvalidInputException`.
- Produces: `CalculatorInput(@NotBlank @Size(max=256) String expression)`; `CalculationResult(String expression, BigDecimal result)`; `CalculatorTool implements Tool<CalculatorInput, CalculationResult>` with `descriptor().name()=="math.calculate"`, risk `DETERMINISTIC`. `AbstractToolContractTest<T extends Tool<?,?>>` with `abstract Tool<?,?> tool();` asserting the universal contract.

- [ ] **Step 1: Write the failing test** (`CalculatorToolTest.java` extending the contract test):
```java
package com.prince.agentic.tool.math;

import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.AbstractToolContractTest;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorToolTest extends AbstractToolContractTest {

    private final CalculatorTool calculator = new CalculatorTool(new ExpressionEvaluator());

    @Override protected Tool<?, ?> tool() { return calculator; }

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
}
```

- [ ] **Step 2: Write `AbstractToolContractTest`** (the reusable contract every tool must satisfy):
```java
package com.prince.agentic.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Universal contract: subclass per tool, implement tool(). Guarantees every M5/M6 tool is well-formed. */
public abstract class AbstractToolContractTest {

    protected abstract Tool<?, ?> tool();

    @Test
    void descriptor_is_present_and_well_formed() {
        ToolDescriptor d = tool().descriptor();
        assertThat(d).isNotNull();
        assertThat(d.name()).isNotBlank();
        assertThat(d.name()).matches("[a-z]+\\.[a-z]+");          // dot-namespaced, lowercase
        assertThat(d.description()).isNotBlank();
        assertThat(d.risk()).isNotNull();
        assertThat(d.inputType()).isNotNull();
        assertThat(d.outputType()).isNotNull();
        assertThat(d.timeout()).isNotNull();
        assertThat(d.requiredRoles()).isNotNull();
    }

    @Test
    void meaningful_tools_require_authentication() {
        assertThat(tool().descriptor().requiresAuthentication())
                .as("all M5 tools require authentication (fail-closed)").isTrue();
    }
}
```

- [ ] **Step 3: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=CalculatorToolTest test
```
Expected: FAIL (CalculatorTool/inputs missing).

- [ ] **Step 4: Create the input/result records and the tool:**
```java
package com.prince.agentic.tool.math;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record CalculatorInput(@NotBlank @Size(max = 256) String expression) {}
```
```java
package com.prince.agentic.tool.math;
import java.math.BigDecimal;
public record CalculationResult(String expression, BigDecimal result) {}
```
```java
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

/** math.calculate — deterministic arithmetic over a safe grammar. No I/O, no side effects. */
@Component
public class CalculatorTool implements Tool<CalculatorInput, CalculationResult> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "math.calculate", "Evaluate a basic arithmetic expression (+, -, *, /, parentheses).",
            "math", "1", ToolRiskLevel.DETERMINISTIC, true, Set.of(RoleNames.ROLE_USER),
            CalculatorInput.class, CalculationResult.class, Duration.ofSeconds(10));

    private final ExpressionEvaluator evaluator;

    public CalculatorTool(ExpressionEvaluator evaluator) { this.evaluator = evaluator; }

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public CalculationResult execute(ToolExecutionContext context, CalculatorInput input) {
        try {
            return new CalculationResult(input.expression(), evaluator.evaluate(input.expression()));
        } catch (IllegalArgumentException e) {
            throw new ToolInvalidInputException("Invalid expression: " + e.getMessage());
        }
    }
}
```
> `ExpressionEvaluator` needs to be a Spring bean for injection. Add `@Component` to it now (edit `ExpressionEvaluator` class) OR construct it in `CalculatorTool` — to keep the evaluator unit-testable standalone AND injectable, annotate `ExpressionEvaluator` with `@Component`.

- [ ] **Step 5: Add `@Component` to `ExpressionEvaluator`.** Edit its class declaration to `@org.springframework.stereotype.Component public class ExpressionEvaluator`.

- [ ] **Step 6: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=CalculatorToolTest test
```
Expected: PASS (contract + behavior).

- [ ] **Step 7: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool/math backend/src/test/java/com/prince/agentic/tool/AbstractToolContractTest.java backend/src/test/java/com/prince/agentic/tool/math/CalculatorToolTest.java
git commit -m "feat: add math.calculate tool + reusable tool contract test [M5]"
```

---

### Task 5: `ToolRegistry` (fail-fast, immutable)

**Files:**
- Create: `tool/ToolRegistry.java`
- Test: `tool/ToolRegistryTest.java`

**Interfaces:**
- Consumes: `List<Tool<?,?>>` (Spring-injected), `ToolRegistrationException`.
- Produces: `ToolRegistry(List<Tool<?,?>>)`; `Tool<?,?> resolve(String name)` (returns null if absent — executor maps to TOOL_NOT_FOUND); `boolean contains(String)`; `List<ToolDescriptor> descriptors()` (immutable, sorted by name); `int size()`.

- [ ] **Step 1: Write the failing test** (`ToolRegistryTest.java`) — use tiny fake tools:
```java
package com.prince.agentic.tool;

import com.prince.agentic.tool.exception.ToolRegistrationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    private Tool<String, String> fake(String name) {
        return new Tool<>() {
            @Override public ToolDescriptor descriptor() {
                return new ToolDescriptor(name, "d", "c", "1", ToolRiskLevel.READ_ONLY, true,
                        Set.of("ROLE_USER"), String.class, String.class, Duration.ofSeconds(10));
            }
            @Override public String execute(ToolExecutionContext c, String in) { return in; }
        };
    }

    @Test
    void registers_and_resolves_by_name() {
        ToolRegistry reg = new ToolRegistry(List.of(fake("task.get"), fake("task.search")));
        assertThat(reg.contains("task.get")).isTrue();
        assertThat(reg.resolve("task.search")).isNotNull();
        assertThat(reg.resolve("nope")).isNull();
        assertThat(reg.size()).isEqualTo(2);
    }

    @Test
    void duplicate_name_fails_fast() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(fake("task.get"), fake("task.get"))))
                .isInstanceOf(ToolRegistrationException.class);
    }

    @Test
    void descriptors_view_is_immutable_and_sorted() {
        ToolRegistry reg = new ToolRegistry(List.of(fake("task.search"), fake("task.get")));
        List<ToolDescriptor> d = reg.descriptors();
        assertThat(d).extracting(ToolDescriptor::name).containsExactly("task.get", "task.search");
        assertThatThrownBy(() -> d.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=ToolRegistryTest test
```
Expected: FAIL.

- [ ] **Step 3: Implement `ToolRegistry`:**
```java
package com.prince.agentic.tool;

import com.prince.agentic.tool.exception.ToolRegistrationException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, fail-fast registry built from all Tool beans at startup. O(1) lookup by name. */
@Component
public class ToolRegistry {

    private final Map<String, Tool<?, ?>> byName;
    private final List<ToolDescriptor> descriptors;

    public ToolRegistry(List<Tool<?, ?>> tools) {
        Map<String, Tool<?, ?>> map = new LinkedHashMap<>();
        for (Tool<?, ?> tool : tools) {
            ToolDescriptor d = requireValid(tool);
            if (map.putIfAbsent(d.name(), tool) != null) {
                throw new ToolRegistrationException("duplicate tool name: " + d.name());
            }
        }
        this.byName = Map.copyOf(map);
        this.descriptors = tools.stream()
                .map(Tool::descriptor)
                .sorted(Comparator.comparing(ToolDescriptor::name))
                .toList();
    }

    private ToolDescriptor requireValid(Tool<?, ?> tool) {
        if (tool == null) throw new ToolRegistrationException("null tool bean");
        ToolDescriptor d = tool.descriptor();
        if (d == null) throw new ToolRegistrationException("tool has null descriptor: " + tool.getClass().getName());
        // ToolDescriptor's compact ctor already validated name/description/risk/types/timeout/roles.
        for (String role : d.requiredRoles()) {
            if (role == null || !role.startsWith("ROLE_")) {
                throw new ToolRegistrationException("invalid role '" + role + "' on tool " + d.name());
            }
        }
        return d;
    }

    public Tool<?, ?> resolve(String name) { return byName.get(name); }
    public boolean contains(String name) { return byName.containsKey(name); }
    public List<ToolDescriptor> descriptors() { return descriptors; }
    public int size() { return byName.size(); }
}
```

- [ ] **Step 4: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=ToolRegistryTest test
```
Expected: PASS.

- [ ] **Step 5: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool/ToolRegistry.java backend/src/test/java/com/prince/agentic/tool/ToolRegistryTest.java
git commit -m "feat: add fail-fast immutable ToolRegistry [M5]"
```

---

### Task 6: `ToolExecutor` (ordered gates → ToolResult)

**Files:**
- Create: `tool/ToolExecutor.java`
- Test: `tool/ToolExecutorTest.java`

**Interfaces:**
- Consumes: `ToolRegistry`, `com.fasterxml.jackson.databind.ObjectMapper`, `jakarta.validation.Validator`, `io.micrometer.core.instrument.MeterRegistry`, `com.prince.agentic.common.exception.ApiException`, the tool exceptions.
- Produces: `ToolResult<Object> execute(String toolName, Map<String,Object> rawArguments, ToolExecutionContext context)`.

- [ ] **Step 1: Write the failing test** (`ToolExecutorTest.java`) with a fake tool + real ObjectMapper/Validator/SimpleMeterRegistry:
```java
package com.prince.agentic.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.common.exception.ResourceNotFoundException;
import com.prince.agentic.security.AuthenticatedUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutorTest {

    record Echo(@NotNull @Min(1) Long id) {}

    private ToolExecutor executor(Tool<?, ?> tool) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        return new ToolExecutor(new ToolRegistry(List.of(tool)), new ObjectMapper(),
                validator, new SimpleMeterRegistry());
    }

    private Tool<Echo, String> echoTool(Set<String> roles, boolean requiresAuth, RuntimeException toThrow) {
        return new Tool<>() {
            @Override public ToolDescriptor descriptor() {
                return new ToolDescriptor("echo.do", "echo", "test", "1", ToolRiskLevel.READ_ONLY,
                        requiresAuth, roles, Echo.class, String.class, Duration.ofSeconds(10));
            }
            @Override public String execute(ToolExecutionContext c, Echo in) {
                if (toThrow != null) throw toThrow;
                return "id=" + in.id() + " user=" + c.principal().userId();
            }
        };
    }

    private ToolExecutionContext user() {
        return ToolExecutionContext.forPrincipal(new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER")));
    }

    @Test
    void happy_path_binds_validates_executes_and_wraps() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("echo.do", Map.of("id", 5), user());
        assertThat(r.success()).isTrue();
        assertThat(r.data()).isEqualTo("id=5 user=7");
        assertThat(r.toolName()).isEqualTo("echo.do");
        assertThat(r.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void unknown_tool_is_TOOL_NOT_FOUND() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("missing.tool", Map.of(), user());
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("TOOL_NOT_FOUND");
    }

    @Test
    void missing_required_role_is_TOOL_FORBIDDEN() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_ADMIN"), true, null))
                .execute("echo.do", Map.of("id", 5), user());   // user lacks ROLE_ADMIN
        assertThat(r.error().code()).isEqualTo("TOOL_FORBIDDEN");
    }

    @Test
    void invalid_input_is_TOOL_INVALID_INPUT() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("echo.do", Map.of("id", 0), user());   // @Min(1)
        assertThat(r.error().code()).isEqualTo("TOOL_INVALID_INPUT");
    }

    @Test
    void unknown_argument_property_is_TOOL_INVALID_INPUT() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, null))
                .execute("echo.do", Map.of("id", 5, "ownerId", 999), user());  // spoofed field
        assertThat(r.error().code()).isEqualTo("TOOL_INVALID_INPUT");
    }

    @Test
    void domain_api_exception_is_surfaced_with_its_code() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true,
                new ResourceNotFoundException("task 5 not found")))   // ApiException with fixed code "NOT_FOUND"
                .execute("echo.do", Map.of("id", 5), user());
        assertThat(r.success()).isFalse();
        assertThat(r.error().code()).isEqualTo("NOT_FOUND");     // preserved, not TOOL_EXECUTION_FAILED
    }

    @Test
    void unexpected_error_is_TOOL_EXECUTION_FAILED() {
        ToolResult<Object> r = executor(echoTool(Set.of("ROLE_USER"), true, new IllegalStateException("boom")))
                .execute("echo.do", Map.of("id", 5), user());
        assertThat(r.error().code()).isEqualTo("TOOL_EXECUTION_FAILED");
    }
}
```
> Confirmed: `ResourceNotFoundException(String message)` is an `ApiException` with a fixed code `"NOT_FOUND"` (404); the executor reads `getCode()`/`getStatus()`, so the surfaced `ToolError.code` is `"NOT_FOUND"`.

- [ ] **Step 2: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=ToolExecutorTest test
```
Expected: FAIL.

- [ ] **Step 3: Implement `ToolExecutor`:**
```java
package com.prince.agentic.tool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prince.agentic.common.exception.ApiException;
import com.prince.agentic.tool.exception.ToolException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/** The single deterministic entry point to run a tool. Enforces the ordered gates and returns a ToolResult. */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final MeterRegistry meters;

    public ToolExecutor(ToolRegistry registry, ObjectMapper objectMapper,
                        Validator validator, MeterRegistry meters) {
        this.registry = registry;
        // A private, strict copy: unknown properties (e.g. a spoofed ownerId) are rejected, not ignored.
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.validator = validator;
        this.meters = meters;
    }

    @SuppressWarnings("unchecked")
    public ToolResult<Object> execute(String toolName, java.util.Map<String, Object> rawArguments,
                                      ToolExecutionContext context) {
        long start = System.nanoTime();
        String risk = "unknown";
        try {
            Tool<?, ?> tool = registry.resolve(toolName);
            if (tool == null) return fail(toolName, "TOOL_NOT_FOUND", "unknown tool: " + toolName, start, risk);
            ToolDescriptor d = tool.descriptor();
            risk = d.risk().name();

            if (d.requiresAuthentication() && (context == null || context.principal() == null)) {
                return fail(toolName, "TOOL_UNAUTHORIZED", "authentication required", start, risk);
            }
            if (!hasRoles(context, d.requiredRoles())) {
                return fail(toolName, "TOOL_FORBIDDEN", "missing required role", start, risk);
            }

            Object input;
            try {
                input = objectMapper.convertValue(rawArguments == null ? java.util.Map.of() : rawArguments,
                        d.inputType());
            } catch (IllegalArgumentException e) {
                return fail(toolName, "TOOL_INVALID_INPUT", "arguments do not match the tool input", start, risk);
            }
            Set<ConstraintViolation<Object>> violations = validator.validate(input);
            if (!violations.isEmpty()) {
                return fail(toolName, "TOOL_INVALID_INPUT", firstMessage(violations), start, risk);
            }

            Object data = ((Tool<Object, Object>) tool).execute(context, input);
            long ms = elapsedMs(start);
            record(toolName, risk, "success", ms, context);
            return ToolResult.ok(toolName, data, ms);

        } catch (ApiException domain) {                 // e.g. NOT_FOUND / FORBIDDEN from a domain service
            return fail(toolName, domain.getCode(), domain.getMessage(), start, risk);
        } catch (RuntimeException unexpected) {
            log.warn("tool.exec unexpected failure tool={} risk={}", toolName, risk, unexpected);
            return fail(toolName, "TOOL_EXECUTION_FAILED", "tool execution failed", start, risk);
        }
    }

    private boolean hasRoles(ToolExecutionContext ctx, Set<String> required) {
        if (required == null || required.isEmpty()) return true;
        if (ctx == null || ctx.principal() == null) return false;
        return ctx.principal().roles().containsAll(required);
    }

    private ToolResult<Object> fail(String tool, String code, String message, long start, String risk) {
        long ms = elapsedMs(start);
        record(tool, risk, "failure", ms, null);
        return ToolResult.failure(tool, new ToolError(code, message), ms);
    }

    private void record(String tool, String risk, String outcome, long ms, ToolExecutionContext ctx) {
        meters.timer("tool.execution.duration", "tool", tool, "risk", risk, "outcome", outcome)
                .record(java.time.Duration.ofMillis(ms));
        meters.counter("tool.execution.result", "tool", tool, "risk", risk, "outcome", outcome).increment();
        Long uid = ctx == null || ctx.principal() == null ? null : ctx.principal().userId();
        log.info("tool.exec tool={} risk={} outcome={} durationMs={} user={}", tool, risk, outcome, ms, uid);
    }

    private long elapsedMs(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000; }

    private String firstMessage(Set<ConstraintViolation<Object>> violations) {
        ConstraintViolation<Object> v = violations.iterator().next();
        return v.getPropertyPath() + " " + v.getMessage();
    }
}
```
> `ToolException` is imported but domain `ApiException` covers tool exceptions too (they extend it); the single `catch (ApiException)` handles both — a thrown `ToolException` surfaces with its own `TOOL_*` code. Keep the import only if used; otherwise remove it to avoid an unused-import warning.

- [ ] **Step 4: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=ToolExecutorTest test
```
Expected: PASS (all 7 cases).

- [ ] **Step 5: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool/ToolExecutor.java backend/src/test/java/com/prince/agentic/tool/ToolExecutorTest.java
git commit -m "feat: add ToolExecutor with ordered authz/validation gates [M5]"
```

---

### Task 7: Task tools (`task.get`, `task.search`, `task.create`)

**Files:**
- Create: `tool/task/TaskGetInput.java`, `TaskSearchInput.java`, `TaskGetTool.java`, `TaskSearchTool.java`, `TaskCreateTool.java`
- Test: `tool/task/TaskGetToolTest.java`, `TaskSearchToolTest.java`, `TaskCreateToolTest.java`

**Interfaces:**
- Consumes: `com.prince.agentic.task.TaskService` (`get(AuthenticatedUser,Long)→TaskResponse`, `list(AuthenticatedUser,TaskStatus,TaskPriority,LocalDate,Integer,Integer,String)→PageResponse<TaskSummaryResponse>`, `create(AuthenticatedUser,TaskCreateRequest)→TaskResponse`); enums `TaskStatus`/`TaskPriority`; `TaskCreateRequest`.
- Produces: `TaskGetInput(@NotNull @Positive Long taskId)`; `TaskSearchInput(TaskStatus status, TaskPriority priority, LocalDate dueBefore, @PositiveOrZero Integer page, @Min(1) @Max(100) Integer size)`; three `@Component` tools implementing `Tool`. `task.get`/`task.search` = `READ_ONLY`, `task.create` = `SIDE_EFFECTING`. Sort is not exposed (pass `null` to the service → its default).

- [ ] **Step 1: Write the failing tests.** `TaskGetToolTest.java` (mock `TaskService`, extend contract):
```java
package com.prince.agentic.tool.task;

import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.tool.AbstractToolContractTest;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolExecutionContext;
import com.prince.agentic.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskGetToolTest extends AbstractToolContractTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskGetTool toolUnderTest = new TaskGetTool(taskService);

    @Override protected Tool<?, ?> tool() { return toolUnderTest; }

    @Test
    void descriptor_is_read_only_task_get() {
        assertThat(toolUnderTest.descriptor().name()).isEqualTo("task.get");
        assertThat(toolUnderTest.descriptor().risk()).isEqualTo(ToolRiskLevel.READ_ONLY);
    }

    @Test
    void execute_passes_principal_and_id_to_service() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "u@x.com", Set.of("ROLE_USER"));
        ToolExecutionContext ctx = ToolExecutionContext.forPrincipal(user);
        TaskResponse expected = mock(TaskResponse.class);
        when(taskService.get(eq(user), eq(42L))).thenReturn(expected);

        assertThat(toolUnderTest.execute(ctx, new TaskGetInput(42L))).isSameAs(expected);
        verify(taskService).get(user, 42L);
    }
}
```
(Analogous `TaskSearchToolTest` — verify `list(user, status, priority, dueBefore, page, size, null)` called; `TaskCreateToolTest` — verify `create(user, request)` called and risk is `SIDE_EFFECTING`.)

- [ ] **Step 2: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=TaskGetToolTest,TaskSearchToolTest,TaskCreateToolTest test
```
Expected: FAIL.

- [ ] **Step 3: Create the inputs.**
```java
package com.prince.agentic.tool.task;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record TaskGetInput(@NotNull @Positive Long taskId) {}
```
```java
package com.prince.agentic.tool.task;
import com.prince.agentic.task.TaskPriority;
import com.prince.agentic.task.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
public record TaskSearchInput(
        TaskStatus status, TaskPriority priority, LocalDate dueBefore,
        @PositiveOrZero Integer page, @Min(1) @Max(100) Integer size) {}
```

- [ ] **Step 4: Create the three tools.** `TaskGetTool`:
```java
package com.prince.agentic.tool.task;

import com.prince.agentic.security.RoleNames;
import com.prince.agentic.task.TaskService;
import com.prince.agentic.task.dto.TaskResponse;
import com.prince.agentic.tool.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** task.get — fetch one of the user's tasks by id (ownership enforced by TaskService). */
@Component
public class TaskGetTool implements Tool<TaskGetInput, TaskResponse> {

    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "task.get", "Get one task owned by the current user, by id.", "task", "1",
            ToolRiskLevel.READ_ONLY, true, Set.of(RoleNames.ROLE_USER),
            TaskGetInput.class, TaskResponse.class, Duration.ofSeconds(10));

    private final TaskService taskService;
    public TaskGetTool(TaskService taskService) { this.taskService = taskService; }

    @Override public ToolDescriptor descriptor() { return DESCRIPTOR; }

    @Override public TaskResponse execute(ToolExecutionContext ctx, TaskGetInput input) {
        return taskService.get(ctx.principal(), input.taskId());   // service enforces ownership / 404-masking
    }
}
```
`TaskSearchTool` (risk READ_ONLY) → `taskService.list(ctx.principal(), in.status(), in.priority(), in.dueBefore(), in.page(), in.size(), null)` returning `PageResponse<TaskSummaryResponse>` (outputType `PageResponse.class`). `TaskCreateTool` (risk **SIDE_EFFECTING**, input `TaskCreateRequest.class`, output `TaskResponse.class`) → `taskService.create(ctx.principal(), input)`.

- [ ] **Step 5: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=TaskGetToolTest,TaskSearchToolTest,TaskCreateToolTest test
```
Expected: PASS.

- [ ] **Step 6: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool/task backend/src/test/java/com/prince/agentic/tool/task
git commit -m "feat: add task.get/search/create tools over TaskService [M5]"
```

---

### Task 8: Customer tools (`customer.get`, `customer.search`)

**Files:**
- Create: `tool/customer/CustomerGetInput.java`, `CustomerSearchInput.java`, `CustomerGetTool.java`, `CustomerSearchTool.java`
- Test: `tool/customer/CustomerGetToolTest.java`, `CustomerSearchToolTest.java`

**Interfaces:**
- Consumes: `com.prince.agentic.customer.CustomerService` (`get(AuthenticatedUser,Long)→CustomerResponse`, `list(AuthenticatedUser,CustomerStatus,String,Integer,Integer,String)→PageResponse<CustomerSummaryResponse>`); `CustomerStatus`.
- Produces: `CustomerGetInput(@NotNull @Positive Long customerId)`; `CustomerSearchInput(CustomerStatus status, @Size(max=200) String search, @PositiveOrZero Integer page, @Min(1) @Max(100) Integer size)`; two `READ_ONLY` `@Component` tools.

- [ ] **Step 1: Write the failing tests** (mirror Task 7's pattern: mock `CustomerService`, extend `AbstractToolContractTest`, assert `customer.get`/`customer.search` names + READ_ONLY, verify the service is called with `ctx.principal()` and the input fields; search passes `null` sort).

- [ ] **Step 2: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=CustomerGetToolTest,CustomerSearchToolTest test
```
Expected: FAIL.

- [ ] **Step 3: Create the inputs and the two tools** — same shape as Task 7. `CustomerGetTool.execute` → `customerService.get(ctx.principal(), in.customerId())`; `CustomerSearchTool.execute` → `customerService.list(ctx.principal(), in.status(), in.search(), in.page(), in.size(), null)`. Both `READ_ONLY`, `requiresAuthentication=true`, role `ROLE_USER`, timeout 10s.

- [ ] **Step 4: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=CustomerGetToolTest,CustomerSearchToolTest test
```
Expected: PASS.

- [ ] **Step 5: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool/customer backend/src/test/java/com/prince/agentic/tool/customer
git commit -m "feat: add customer.get/search tools over CustomerService [M5]"
```

---

### Task 9: Security + full-context integration tests (real services, H2)

**Files:**
- Test: `tool/ToolSecurityTest.java`

**Interfaces:**
- Consumes: real Spring context (`@SpringBootTest`, `@ActiveProfiles("test")`), `ToolExecutor`, `ToolRegistry`, `TaskService` (real, over H2), auth helpers (register/login as in `TaskApiTest`) to obtain real `AuthenticatedUser`s — or build `AuthenticatedUser` + persist a task via `TaskService` directly.
- Produces: end-to-end proof the executor + real domain tools enforce ownership/roles.

- [ ] **Step 1: Write `ToolSecurityTest`** (`@SpringBootTest @ActiveProfiles("test") @Transactional`). Autowire `ToolExecutor`, `ToolRegistry`, `TaskService`. Build two principals (`AuthenticatedUser(idA,…,ROLE_USER)`, `AuthenticatedUser(idB,…,ROLE_USER)`, and an admin). Assert:
```java
// Arrange: create a task as user A via taskService.create(userA, req) -> capture id.
// 1. task.get by user A -> ToolResult.success, data is A's task.
// 2. task.get same id by user B -> failure, error.code == "NOT_FOUND" (404-masking preserved).
// 3. task.get same id by ADMIN -> success (admin-any-by-id preserved).
// 4. task.create by user A with {"title":"via tool","priority":"HIGH"} -> success; owner is A (not spoofable).
// 5. execute("task.get", Map.of("id",1), anonymousContext) where context.principal()==null -> "TOOL_UNAUTHORIZED".
// 6. execute("task.get", Map.of("taskId",1,"ownerId",999), ctxA) -> "TOOL_INVALID_INPUT" (unknown property rejected).
// 7. registry.contains("task.delete") == false (destructive tool not registered in M5).
```
Build the users to match how real ids are assigned (persist via the auth flow or `UserRepository`, mirroring `TaskApiTest.adminToken`). Use `ToolExecutionContext.forPrincipal(user)`.

- [ ] **Step 2: Run it.**
```bash
cd backend && ./mvnw -q -Dtest=ToolSecurityTest test
```
Expected: PASS (Ollama/Docker not required — H2 only).

- [ ] **Step 3: Commit (checkpoint).**
```bash
git add backend/src/test/java/com/prince/agentic/tool/ToolSecurityTest.java
git commit -m "test: tool executor security/ownership integration over real services [M5]"
```

---

### Task 10: Read-only tool catalog endpoint (`GET /api/v1/tools`, ADMIN)

**Files:**
- Create: `tool/api/dto/ToolDescriptorResponse.java`, `tool/api/ToolCatalogController.java`
- Test: `tool/ToolCatalogApiTest.java`
- Modify: `backend/pom.xml` (JaCoCo exclude `com/prince/agentic/tool/api/**`)

**Interfaces:**
- Consumes: `ToolRegistry.descriptors()`.
- Produces: `GET /api/v1/tools` (ADMIN) → `List<ToolDescriptorResponse>`; `ToolDescriptorResponse(name, description, category, version, risk, requiresAuthentication, requiredRoles, inputType, outputType)` where types are **simple class names**.

- [ ] **Step 1: Write the failing test** (`ToolCatalogApiTest.java`, `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`, register/login helper like `TaskApiTest`):
```java
// GET /api/v1/tools as ADMIN -> 200, body is a JSON array containing name "task.get",
//   entries expose "risk" and "inputType" (simple name, e.g. "TaskGetInput"), and DO NOT contain
//   "TaskGetTool" (no implementation class name).
// GET /api/v1/tools as USER -> 403 (jsonPath $.error == "FORBIDDEN").
// GET /api/v1/tools anonymous -> 401.
```

- [ ] **Step 2: Run to verify it fails.**
```bash
cd backend && ./mvnw -q -Dtest=ToolCatalogApiTest test
```
Expected: FAIL.

- [ ] **Step 3: Create the response DTO + mapping** (types rendered as `Class::getSimpleName`):
```java
package com.prince.agentic.tool.api.dto;

import com.prince.agentic.tool.ToolDescriptor;
import java.util.List;

public record ToolDescriptorResponse(
        String name, String description, String category, String version, String risk,
        boolean requiresAuthentication, List<String> requiredRoles, String inputType, String outputType) {

    public static ToolDescriptorResponse from(ToolDescriptor d) {
        return new ToolDescriptorResponse(d.name(), d.description(), d.category(), d.version(),
                d.risk().name(), d.requiresAuthentication(), List.copyOf(d.requiredRoles()),
                d.inputType().getSimpleName(), d.outputType().getSimpleName());
    }
}
```

- [ ] **Step 4: Create the controller** (thin; ADMIN-gated like `AdminController`):
```java
package com.prince.agentic.tool.api;

import com.prince.agentic.tool.ToolRegistry;
import com.prince.agentic.tool.api.dto.ToolDescriptorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only, ADMIN-only catalog of registered tools (metadata only). For debugging/inspection. */
@RestController
@RequestMapping("/api/v1/tools")
@Tag(name = "Tools", description = "Registered tool metadata (ADMIN only; read-only)")
public class ToolCatalogController {

    private final ToolRegistry registry;
    public ToolCatalogController(ToolRegistry registry) { this.registry = registry; }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List registered tool descriptors (metadata only)")
    public List<ToolDescriptorResponse> list() {
        return registry.descriptors().stream().map(ToolDescriptorResponse::from).toList();
    }
}
```

- [ ] **Step 5: Add the JaCoCo exclude.** In `backend/pom.xml` `jacoco` `<excludes>`, add:
```xml
<exclude>com/prince/agentic/tool/api/**</exclude>
```

- [ ] **Step 6: Run to verify it passes.**
```bash
cd backend && ./mvnw -q -Dtest=ToolCatalogApiTest test
```
Expected: PASS.

- [ ] **Step 7: Commit (checkpoint).**
```bash
git add backend/src/main/java/com/prince/agentic/tool/api backend/src/test/java/com/prince/agentic/tool/ToolCatalogApiTest.java backend/pom.xml
git commit -m "feat: add ADMIN read-only GET /api/v1/tools catalog [M5]"
```

---

### Task 11: Architecture boundary test (AI/persistence isolation)

**Files:**
- Test: `tool/ToolArchitectureBoundaryTest.java`

**Interfaces:**
- Produces: a source-scan test guaranteeing `tool.*` imports no `ai.*`, `EntityManager`, `JdbcTemplate`, or `*Repository`.

- [ ] **Step 1: Write the test** (mirror M4's `ai/ArchitectureBoundaryTest`, scanning `src/main/java/com/prince/agentic/tool`):
```java
package com.prince.agentic.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ToolArchitectureBoundaryTest {

    private static final Path TOOL_ROOT = Path.of("src/main/java/com/prince/agentic/tool");

    @Test
    void tool_subsystem_does_not_depend_on_ai_or_persistence() throws Exception {
        try (Stream<Path> files = Files.walk(TOOL_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String src;
                try { src = Files.readString(p); } catch (Exception e) { throw new RuntimeException(e); }
                assertThat(src).as("%s must not import ai/persistence internals", p)
                        .doesNotContain("com.prince.agentic.ai")
                        .doesNotContain("org.springframework.ai")
                        .doesNotContain("jakarta.persistence.EntityManager")
                        .doesNotContain("org.springframework.jdbc.core.JdbcTemplate")
                        .doesNotContain("Repository;");   // no direct repository imports
            });
        }
    }
}
```
> The `"Repository;"` check catches `import ...TaskRepository;` while allowing the word elsewhere. If a false positive arises, tighten to a regex over import lines.

- [ ] **Step 2: Run it.**
```bash
cd backend && ./mvnw -q -Dtest=ToolArchitectureBoundaryTest test
```
Expected: PASS.

- [ ] **Step 3: Commit (checkpoint).**
```bash
git add backend/src/test/java/com/prince/agentic/tool/ToolArchitectureBoundaryTest.java
git commit -m "test: enforce tool subsystem isolation from AI/persistence [M5]"
```

---

### Task 12: Documentation + ADRs

**Files:**
- Create: `docs/ADR/0011-tool-abstraction-and-registry.md`, `docs/ADR/0012-tool-authorization-and-execution-context.md`
- Modify: `docs/ADR/README.md`, `docs/TOOL_SYSTEM.md`, `docs/AGENT_ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/GUARDRAILS.md`, `docs/EVALUATION.md`, `docs/OBSERVABILITY.md`, `docs/API.md`, `docs/TESTING.md`, `docs/PERFORMANCE.md`, `docs/ROADMAP.md`, `docs/TECH_STACK.md`, `docs/CHANGELOG.md`, `README.md`, `backend/README.md`

- [ ] **Step 1: Write ADR-0011** (tool abstraction & registry) using the `docs/ADR/README.md` template. Decision: `Tool<I,O>` + `ToolDescriptor`; Spring bean discovery (no annotation); fail-fast immutable registry; **executor returns `ToolResult<O>`, handler returns raw O**; schema-via-`Class` (no engine); Spring-AI adapter deferred to M6. Alternatives: `@AgentTool` annotation (rejected — duplicates descriptor), raw-O return (rejected — agent needs structured observations), a JSON-schema engine now (rejected — Spring AI generates it in M6). Consequences: reusable framework, Spring-AI-free.

- [ ] **Step 2: Write ADR-0012** (tool authorization & execution-context boundary). Decision: identity from the authenticated principal in `ToolExecutionContext` (never from arguments; unknown JSON props rejected); **two layers** — role/tool-type in the executor, resource/ownership delegated to the domain service; fail-closed ordered gates; risk metadata present, hard timeout/confirmation deferred to M8. Alternatives: single collapsed check (rejected), context from arguments (rejected — the core security hole). Consequences: REST and tool paths share one ownership implementation; the LLM can never manufacture identity.

- [ ] **Step 3: Add both ADR rows** to `docs/ADR/README.md` "Accepted ADRs" and remove the "Tool authorization model" line from *Planned* (satisfied by ADR-0012).

- [ ] **Step 4: Rewrite `docs/TOOL_SYSTEM.md`** to describe the **implemented** framework: apply **R1** (risk enum `READ_ONLY/DETERMINISTIC/SIDE_EFFECTING/HIGH_RISK` — remove `LOW_RISK` phrasing) and **R2** (dot-namespaced names `task.get` … in §4/§7); add the execution pipeline (§5 of the spec), the registry, the `ToolResult` envelope, the six registered tools with their risk, the timeout-is-metadata note, and the `GET /api/v1/tools` endpoint. Mark the agent bridge as **M6 PLANNED**.

- [ ] **Step 5: Update the remaining docs** (per spec §19), each labeled M5 IMPLEMENTED / M6 PLANNED: `AGENT_ARCHITECTURE.md` (framework now exists below the future agent; state the §58 invariant), `SECURITY.md` (tool threat model + identity invariant), `GUARDRAILS.md` (risk metadata + timeout hooks present; enforcement M8), `EVALUATION.md` (tool contract tests vs agent-selection eval boundary — R3), `OBSERVABILITY.md` (`tool.execution.*` metrics), `API.md` (`GET /api/v1/tools`, ADMIN, + `TOOL_*` error codes), `TESTING.md` (M5 test layers), `PERFORMANCE.md` (O(1) registry, no numbers), `ROADMAP.md` (M5 → ✅ with delivered/validation), `TECH_STACK.md` (no new deps — "Added in M5: none; framework uses existing Spring/Jackson/Micrometer"), `CHANGELOG.md` (0.0.5 entry), `README.md` + `backend/README.md` (tools + `/api/v1/tools`).

- [ ] **Step 6: Commit (checkpoint).**
```bash
git add docs README.md backend/README.md
git commit -m "docs: M5 tool framework — ADR-0011/0012 and doc updates"
```

---

### Task 13: Full verification

**Files:** none.

- [ ] **Step 1: Full clean test.**
```bash
cd backend && ./mvnw -q clean test
```
Expected: PASS (146 prior + new tool tests). Confirm the context boots with the `ToolRegistry` validating all six tools.

- [ ] **Step 2: Full verify with coverage gate.**
```bash
cd backend && ./mvnw -q clean verify
```
Expected: PASS; JaCoCo BUNDLE ≥ 0.75 held; Testcontainers ITs run for real if Docker present, else skip; no Ollama needed.

- [ ] **Step 3: Boot smoke check (optional).**
```bash
cd backend && ./mvnw -q -DskipTests spring-boot:run   # needs PostgreSQL + env; else skip
# With a JWT: GET /api/v1/tools as ADMIN -> 200 list of 6 tools; as USER -> 403. Stop the app after.
```
Record honestly. Report final status per spec §15.

- [ ] **Step 4: Review (no commit/push).**
```bash
git status && git diff --stat
```
Leave changes staged for the human.

---

## Self-Review (completed by plan author)

**1. Spec coverage:** D1–D2 → Task 1/6; D3–D4 → Task 5; D5/D6 → Task 1 + naming throughout + contract test (Task 4); D7 → Task 1 descriptor; D8/D9/D10/D11 → Task 6 (+ Task 1 context); D12/D13/D14 → Tasks 4/7/8; D15/D16 → Task 2 + Task 6; D17 → Task 1 descriptor + Task 6 duration (metadata only, documented); D18 → Task 3/4; D19 → Task 1 descriptor (Class types) + Task 10 (simple names); D20 → Task 11 (boundary) + Task 12 (docs); D21 → Task 10; D22 → Task 6 metrics (no tables); D23 → package layout throughout; D24 → Task 12; D25 → Task 10 (JaCoCo exclude) + Task 13. Security model §8 → Tasks 6/9/11. Reconciliations R1/R2/R3 → Task 12. Every spec section maps to a task.

**2. Placeholder scan:** No TBD/TODO. Tasks 7/8 abbreviate the second/third near-identical tool bodies but give the exact service calls, types, risk levels, and one full worked example (`TaskGetTool`) to copy — not a vague "similar to". Task 3 and Task 6 contain full implementations.

**3. Type consistency:** `Tool<I,O>`, `ToolDescriptor(...)` 10-arg order, `ToolExecutionContext.forPrincipal`, `ToolResult.ok/failure`, `ToolError(code,message)`, `ToolExecutor.execute(String, Map, ToolExecutionContext)`, risk enum values, and error codes are identical across Tasks 1/2/5/6/7/8/9/10. Service signatures match the real `TaskService`/`CustomerService` (verified in the spec). One caution flagged in Task 6 to confirm `ResourceNotFoundException`'s constructor before running its test.

**Executor order note:** authorization (role) precedes input binding/validation, which precedes execution — matches spec §5 and TOOL_SYSTEM.md §3. Do not reorder.
