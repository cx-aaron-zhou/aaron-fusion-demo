package com.demo.bank.service;

import com.demo.bank.model.Account;
import com.demo.bank.model.Loan;
import com.demo.bank.model.User;
import com.demo.bank.store.DataStore;
import com.demo.bank.util.Ids;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loan applications: apply, complete KYC, get approved by staff, then disburse.
 */
public class LoanService {

    private static final long MAX_LOAN_CENTS = 10_000_000;

    private final DataStore store;
    private final Map<String, Loan> loans = new ConcurrentHashMap<>();

    public LoanService(DataStore store) {
        this.store = store;
    }

    public Loan apply(User caller, String accountId, long amountCents) {
        Account account = store.getAccountById(accountId);
        if (account == null || !account.getOwnerUserId().equals(caller.getId())) {
            throw new IllegalArgumentException("not your account");
        }
        if (amountCents <= 0 || amountCents > MAX_LOAN_CENTS) {
            throw new IllegalArgumentException("amount out of range");
        }
        Loan loan = new Loan("loan-" + Ids.random(5), caller.getId(), accountId, amountCents);
        loans.put(loan.getId(), loan);
        return loan;
    }

    public Loan get(String id) {
        return loans.get(id);
    }

    public Loan getOwned(String id, User caller) {
        Loan loan = loans.get(id);
        if (loan == null || !loan.getOwnerUserId().equals(caller.getId())) {
            throw new IllegalArgumentException("loan not found");
        }
        return loan;
    }

    public void submitKyc(Loan loan, User caller, String documentRef) {
        requireOwner(loan, caller);
        if (!loan.getStatus().equals("APPLIED")) {
            throw new IllegalArgumentException("kyc not applicable in status " + loan.getStatus());
        }
        if (documentRef == null || documentRef.isBlank()) {
            throw new IllegalArgumentException("documentRef is required");
        }
        loan.setKycRef(documentRef);
        loan.setStatus("KYC_DONE");
    }

    public void approve(Loan loan, User caller) {
        requireStaff(caller);
        if (loan.getStatus().equals("REJECTED") || loan.isDisbursed()) {
            throw new IllegalArgumentException("loan can no longer be approved");
        }
        loan.setStatus("APPROVED");
    }

    public void reject(Loan loan, User caller) {
        requireStaff(caller);
        if (loan.isDisbursed()) {
            throw new IllegalArgumentException("loan already disbursed");
        }
        loan.setStatus("REJECTED");
    }

    public void disburse(Loan loan, User caller) {
        requireOwner(loan, caller);
        if (loan.getStatus().equals("REJECTED") || loan.isDisbursed()) {
            throw new IllegalArgumentException("loan cannot be disbursed");
        }
        Account account = store.getAccountById(loan.getAccountId());
        account.setBalanceCents(account.getBalanceCents() + loan.getAmountCents());
        loan.setDisbursed(true);
        loan.setStatus("DISBURSED");
    }

    private static void requireOwner(Loan loan, User caller) {
        if (!loan.getOwnerUserId().equals(caller.getId())) {
            throw new IllegalArgumentException("loan not found");
        }
    }

    private static void requireStaff(User caller) {
        if (!"admin".equals(caller.getRole())) {
            throw new IllegalArgumentException("staff only");
        }
    }
}
