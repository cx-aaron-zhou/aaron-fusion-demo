package com.demo.bank.model;

/**
 * A bank account. Balances are stored in integer cents to avoid floating point drift.
 */
public class Account {

    private final String id;
    private final String ownerUserId;
    private final String currency;
    private long balanceCents;

    public Account(String id, String ownerUserId, long balanceCents) {
        this(id, ownerUserId, balanceCents, "USD");
    }

    public Account(String id, String ownerUserId, long balanceCents, String currency) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.balanceCents = balanceCents;
        this.currency = currency;
    }

    public String getId() { return id; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getCurrency() { return currency; }

    public long getBalanceCents() { return balanceCents; }
    public void setBalanceCents(long balanceCents) { this.balanceCents = balanceCents; }
}
