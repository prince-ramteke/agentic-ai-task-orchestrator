package com.prince.agentic.guardrail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.prince.agentic.tool.ToolRiskLevel;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Action fingerprinting for confirmation integrity (spec §6.1). A confirmation is bound to a
 * SHA-256 hex digest over exactly five fields: {@code userId}, {@code conversationId},
 * {@code toolName}, canonical arguments, and {@code riskLevel}. If <em>any</em> bound field changes,
 * the fingerprint changes — so a mutated, replayed, cross-user, or cross-conversation request can
 * never match an approval.
 *
 * <p>Arguments are canonicalized to a deterministic, sorted-key JSON string so that logically equal
 * argument maps always produce the same digest regardless of insertion order.
 */
@Service
public class FingerprintService {

    // A unit-separator control char cannot appear literally in the canonical JSON (Jackson escapes
    // control characters), the UUID conversationId, the dotted tool name, the enum, or the number —
    // so it is an unforgeable field boundary that removes prefix ambiguity between adjacent fields.
    private static final String SEP = "";

    private final ObjectMapper canonicalMapper;

    public FingerprintService(ObjectMapper mapper) {
        // A private copy that always orders map entries by key → deterministic serialization.
        this.canonicalMapper = mapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /** Deterministic, sorted-key JSON for an argument map (null/empty → {@code "{}"}). */
    public String canonicalArguments(Map<String, Object> arguments) {
        Map<String, Object> sorted = (arguments == null) ? Map.of() : new TreeMap<>(arguments);
        try {
            return canonicalMapper.writeValueAsString(sorted);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("arguments are not canonicalizable", e);
        }
    }

    /** SHA-256 hex over the five bound fields, joined by an unforgeable separator. */
    public String fingerprint(long userId, String conversationId, String toolName,
                              String canonicalArgumentsJson, ToolRiskLevel riskLevel) {
        String material = String.join(SEP,
                Long.toString(userId),
                conversationId == null ? "" : conversationId,
                toolName,
                riskLevel.name(),
                canonicalArgumentsJson);
        return sha256Hex(material);
    }

    /**
     * SHA-256 hex of an argument map's canonical form (M9 audit {@code arguments_hash}). Reuses the
     * same deterministic, sorted-key canonicalization as the confirmation fingerprint, so a hash is
     * stable and never contains the raw argument values. Null/empty → hash of {@code "{}"}.
     */
    public String argumentsHashHex(Map<String, Object> arguments) {
        return sha256Hex(canonicalArguments(arguments));
    }

    private static String sha256Hex(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a required JVM algorithm; absence is a fatal environment error.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
