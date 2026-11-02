package com.demo.bank.service;

import com.demo.bank.model.Account;
import com.demo.bank.model.Txn;
import com.demo.bank.store.DataStore;
import com.demo.bank.util.Ids;

/**
 * Account lookups and money movement.
 */
public class AccountService {

    private final DataStore store;
    private final NotificationService notifications;

    public AccountService(DataStore store, NotificationService notifications) {
        this.store = store;
        this.notifications = notifications;
    }

    public Account getAccount(String accountId) {
        return store.getAccountById(accountId);
    }

    /** Transfers and, if applied, records it in the ledger and notifies the recipient. */
    public boolean transfer(String fromAccountId, String toAccountId, long amountCents, String memo) {
        boolean ok = transfer(fromAccountId, toAccountId, amountCents);
        if (ok) {
            Txn txn = new Txn(Ids.random(6), fromAccountId, toAccountId, amountCents, memo == null ? "" : memo);
            store.addTxn(txn);
            notifications.enqueue(txn);
        }
        return ok;
    }

    /**
     * Moves {@code amountCents} from one account to another.
     *
     * @return true if the transfer was applied, false if an account was missing
     *         or the source had insufficient funds.
     */
    public boolean transfer(String fromAccountId, String toAccountId, long amountCents) {
        Account from = store.getAccountById(fromAccountId);
        Account to = store.getAccountById(toAccountId);
        if (from == null || to == null) {
            return false;
        }

        // Make sure the source can cover the transfer before moving anything.
        if (from.getBalanceCents() < amountCents) {
            return false;
        }

        long newFrom = from.getBalanceCents() - amountCents;
        long newTo = to.getBalanceCents() + amountCents;

        from.setBalanceCents(newFrom);
        to.setBalanceCents(newTo);
        return true;
    }
}
