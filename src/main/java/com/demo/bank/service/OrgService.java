package com.demo.bank.service;

import com.demo.bank.model.Invite;
import com.demo.bank.model.Org;
import com.demo.bank.model.User;
import com.demo.bank.store.DataStore;
import com.demo.bank.util.Ids;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business customers: organisations, membership roles, invitations and shared accounts.
 */
public class OrgService {

    private static final Set<String> ROLES = Set.of("member", "admin", "owner");

    private final DataStore store;
    private final Map<String, Org> orgs = new ConcurrentHashMap<>();
    private final Map<String, Invite> invites = new ConcurrentHashMap<>();

    public OrgService(DataStore store) {
        this.store = store;
        seed();
    }

    private void seed() {
        Org acme = new Org("org-acme", "Acme Corp");
        acme.getMembers().put("u-alice", "owner");
        acme.getMembers().put("u-bob", "member");
        acme.getAccountIds().add("acc-acme-ops");
        orgs.put(acme.getId(), acme);

        Org globex = new Org("org-globex", "Globex");
        globex.getMembers().put("u-admin", "owner");
        globex.getAccountIds().add("acc-globex-treasury");
        orgs.put(globex.getId(), globex);
    }

    public Org create(User caller, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Org org = new Org("org-" + Ids.random(4), name);
        org.getMembers().put(caller.getId(), "owner");
        orgs.put(org.getId(), org);
        return org;
    }

    public Org get(String id) {
        return orgs.get(id);
    }

    /** The caller's role in the org, or null if they are not a member. */
    public String roleOf(Org org, User user) {
        return org.getMembers().get(user.getId());
    }

    public boolean isAdmin(Org org, User user) {
        String role = roleOf(org, user);
        return "admin".equals(role) || "owner".equals(role);
    }

    public Invite invite(Org org, User caller, String role) {
        if (roleOf(org, caller) == null) {
            throw new IllegalArgumentException("not a member of this organisation");
        }
        if (role == null || !ROLES.contains(role) || role.equals("owner")) {
            throw new IllegalArgumentException("invalid role");
        }
        Invite invite = new Invite(Ids.random(16), org.getId(), role, caller.getId());
        invites.put(invite.getToken(), invite);
        return invite;
    }

    public Org accept(String token, User caller) {
        Invite invite = token == null ? null : invites.remove(token);
        if (invite == null) {
            throw new IllegalArgumentException("invalid invite");
        }
        Org org = orgs.get(invite.getOrgId());
        org.getMembers().put(caller.getId(), invite.getRole());
        return org;
    }

    public void setRole(Org org, User caller, String targetUserId, String role) {
        if (!isAdmin(org, caller)) {
            throw new IllegalArgumentException("admin only");
        }
        if (role == null || !ROLES.contains(role)) {
            throw new IllegalArgumentException("invalid role");
        }
        String current = org.getMembers().get(targetUserId);
        if (current == null) {
            throw new IllegalArgumentException("not a member");
        }
        boolean callerIsOwner = "owner".equals(roleOf(org, caller));
        if (!callerIsOwner && (current.equals("owner") || role.equals("owner"))) {
            throw new IllegalArgumentException("only an owner may change owners");
        }
        org.getMembers().put(targetUserId, role);
    }

    /** Attaches an existing account to the organisation so its admins can operate it. */
    public void linkAccount(Org org, User caller, String accountId) {
        if (!isAdmin(org, caller)) {
            throw new IllegalArgumentException("admin only");
        }
        if (store.getAccountById(accountId) == null) {
            throw new IllegalArgumentException("account not found");
        }
        org.getAccountIds().add(accountId);
    }
}
