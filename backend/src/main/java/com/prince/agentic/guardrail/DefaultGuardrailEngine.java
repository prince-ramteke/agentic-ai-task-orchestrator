package com.prince.agentic.guardrail;

import com.prince.agentic.agent.AgentDecision;
import com.prince.agentic.security.AuthenticatedUser;
import com.prince.agentic.tool.Tool;
import com.prince.agentic.tool.ToolDescriptor;
import com.prince.agentic.tool.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Deterministic policy engine (spec §4). Resolves the authoritative {@link ToolDescriptor} from the
 * {@link ToolRegistry}, then runs the ordered {@link GuardrailPolicy} beans and returns the first
 * non-{@code ALLOW} decision (else {@code ALLOW}). Adding a policy is adding a bean — the engine is
 * closed for modification.
 *
 * <p><b>Unknown tool</b> → {@code ALLOW}: the engine defers to {@code ToolExecutor}, which maps an
 * unresolved tool to a {@code TOOL_NOT_FOUND} observation, preserving the M6 recovery path. No effect
 * can occur, because there is no tool to run.
 *
 * <p><b>Purity:</b> {@code evaluate} has no side effects beyond emitting a metric, so policy outcomes
 * are fully deterministic and testable. Identity comes only from the verified principal; no user or
 * model text is ever a policy input.
 */
@Service
public class DefaultGuardrailEngine implements GuardrailEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultGuardrailEngine.class);

    private final ToolRegistry registry;
    private final List<GuardrailPolicy> policies;
    private final MeterRegistry meters;

    public DefaultGuardrailEngine(ToolRegistry registry, List<GuardrailPolicy> policies,
                                  MeterRegistry meters) {
        this.registry = registry;
        // Fix the evaluation order once (ascending); ties keep bean order deterministically.
        this.policies = policies.stream()
                .sorted(Comparator.comparingInt(GuardrailPolicy::order))
                .toList();
        this.meters = meters;
    }

    @Override
    public GuardrailDecision evaluate(AuthenticatedUser principal, AgentDecision decision,
                                      GuardrailContext ctx) {
        Tool<?, ?> tool = decision.tool() == null ? null : registry.resolve(decision.tool());
        if (tool == null) {
            // No resolvable tool → no effect possible; defer to ToolExecutor's TOOL_NOT_FOUND path.
            return record(GuardrailDecision.allow(), decision.tool(), "unknown");
        }
        ToolDescriptor descriptor = tool.descriptor();
        GuardrailInput input = new GuardrailInput(principal, decision, descriptor, ctx);

        for (GuardrailPolicy policy : policies) {
            GuardrailDecision d = policy.evaluate(input);
            if (d.outcome() != GuardrailOutcome.ALLOW) {
                return record(d, decision.tool(), descriptor.risk().name());
            }
        }
        return record(GuardrailDecision.allow(), decision.tool(), descriptor.risk().name());
    }

    private GuardrailDecision record(GuardrailDecision d, String tool, String risk) {
        String metric = switch (d.outcome()) {
            case ALLOW -> "guardrail.allow";
            case DENY -> "POLICY_VIOLATION".equals(d.reasonCode())
                    ? "guardrail.policy_violation" : "guardrail.deny";
            case REQUIRE_CONFIRMATION -> "guardrail.confirmation_required";
        };
        meters.counter(metric, "tool", tool == null ? "none" : tool, "riskLevel", risk).increment();
        if (d.outcome() != GuardrailOutcome.ALLOW) {
            log.info("guardrail.decision tool={} risk={} outcome={} reason={} policy={}",
                    tool, risk, d.outcome(), d.reasonCode(), d.policyId());
        }
        return d;
    }
}
