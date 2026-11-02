package com.demo.bank.model;

/**
 * A loan application. Status moves APPLIED -> KYC_DONE -> APPROVED -> DISBURSED,
 * or to REJECTED.
 */
public class Loan {

    private final String id;
    private final String ownerUserId;
    private final String accountId;
    private final long amountCents;
    private volatile String status = "APPLIED";
    private volatile String kycRef;
    private volatile boolean disbursed;

    public Loan(String id, String ownerUserId, String accountId, long amountCents) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.accountId = accountId;
        this.amountCents = amountCents;
    }

    public String getId() { return id; }
    public String getOwnerUserId() { return ownerUserId; }
    public String getAccountId() { return accountId; }
    public long getAmountCents() { return amountCents; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getKycRef() { return kycRef; }
    public void setKycRef(String kycRef) { this.kycRef = kycRef; }

    public boolean isDisbursed() { return disbursed; }
    public void setDisbursed(boolean disbursed) { this.disbursed = disbursed; }
}
