package com.demo.bank.model;

/**
 * A completed money movement between two accounts.
 */
public class Txn {

    private final String id;
    private final String fromAccountId;
    private final String toAccountId;
    private final long amountCents;
    private final String memo;
    private final long createdAt;

    public Txn(String id, String fromAccountId, String toAccountId, long amountCents, String memo) {
        this.id = id;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amountCents = amountCents;
        this.memo = memo;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getFromAccountId() { return fromAccountId; }
    public String getToAccountId() { return toAccountId; }
    public long getAmountCents() { return amountCents; }
    public String getMemo() { return memo; }
    public long getCreatedAt() { return createdAt; }
}
