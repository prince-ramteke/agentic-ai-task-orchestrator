package com.prince.agentic.admin;

/**
 * Trivial admin-scoped response used to demonstrate and test role-based access control.
 */
public record AdminPingResponse(String status, String scope) {
}
