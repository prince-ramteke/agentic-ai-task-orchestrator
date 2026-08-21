package com.prince.agentic.tool;

/**
 * A registered, permission-controlled backend capability — the <b>only</b> bridge from the future
 * agent (M6) to data or effects. A tool is not an arbitrary Java method: it is exposed through a
 * typed, validated, authorized execution boundary (see {@link ToolExecutor}).
 *
 * <p>The handler returns raw {@code O} and throws a typed exception on failure; the
 * {@link ToolExecutor} wraps the outcome into a {@link ToolResult}. Input has already been bound and
 * validated by the executor before {@link #execute} is called. Resource-ownership authorization is
 * delegated to the domain service the tool wraps (the tool passes the authenticated principal from
 * the {@link ToolExecutionContext}); the tool never re-implements ownership and never touches a
 * repository directly.
 *
 * @param <I> typed, validated input
 * @param <O> typed output (a DTO/result model — never a JPA entity)
 */
public interface Tool<I, O> {

    /** Immutable metadata describing this tool (name, risk, auth policy, input/output types, timeout). */
    ToolDescriptor descriptor();

    /**
     * Execute over already-validated input. Return raw {@code O}; throw a typed exception
     * ({@code ToolException} or a domain {@code ApiException}) on failure.
     */
    O execute(ToolExecutionContext context, I input);
}
