package com.demo.bank.model;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A business customer (tenant). Members have a role of "member", "admin" or "owner",
 * and the organisation can have shared accounts that its admins operate.
 */
public class Org {

    private final String id;
    private final String name;
    private final Map<String, String> members = new ConcurrentHashMap<>();   // userId -> role
    private final Set<String> accountIds = ConcurrentHashMap.newKeySet();

    public Org(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, String> getMembers() { return members; }
    public Set<String> getAccountIds() { return accountIds; }
}
