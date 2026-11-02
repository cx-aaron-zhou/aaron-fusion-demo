package com.demo.bank.model;

/**
 * A pending invitation to join an {@link Org} with a given role.
 */
public class Invite {

    private final String token;
    private final String orgId;
    private final String role;
    private final String createdBy;

    public Invite(String token, String orgId, String role, String createdBy) {
        this.token = token;
        this.orgId = orgId;
        this.role = role;
        this.createdBy = createdBy;
    }

    public String getToken() { return token; }
    public String getOrgId() { return orgId; }
    public String getRole() { return role; }
    public String getCreatedBy() { return createdBy; }
}
