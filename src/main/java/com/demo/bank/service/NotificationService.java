package com.demo.bank.service;

import com.demo.bank.model.Account;
import com.demo.bank.model.Txn;
import com.demo.bank.model.User;
import com.demo.bank.store.DataStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns completed transfers into inbox messages for the receiving customer. Transfers
 * are queued when they complete and a background job drains the queue and renders the
 * message text, which supports {{merge}} fields such as {{amount}} and {{from}}.
 */
public class NotificationService {
    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());
    private static final Pattern FIELD =
            Pattern.compile("\\{\\{([a-z]+)(?::([A-Za-z0-9_-]+)\\.([A-Za-z]+))?\\}\\}");

    private final DataStore store;
    private final BlockingQueue<Txn> queue = new LinkedBlockingQueue<>();
    private final Map<String, List<String>> inboxByUserId = new ConcurrentHashMap<>();

    public NotificationService(DataStore store) {
        this.store = store;
    }

    public void enqueue(Txn txn) {
        queue.add(txn);
    }

    public List<String> inboxFor(String userId) {
        return inboxByUserId.getOrDefault(userId, List.of());
    }

    /** Job entry point: delivers everything currently queued. */
    public void drain() {
        Txn txn;
        while ((txn = queue.poll()) != null) {
            try {
                deliver(txn);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "notification delivery failed", e);
            }
        }
    }

    private void deliver(Txn txn) {
        Account to = store.getAccountById(txn.getToAccountId());
        if (to == null) {
            return;
        }
        String template = "You received {{amount}} cents from {{from}}. Note: " + txn.getMemo();
        String text = render(template, txn);
        inboxByUserId.computeIfAbsent(to.getOwnerUserId(), k -> new CopyOnWriteArrayList<>()).add(text);
    }

    private String render(String template, Txn txn) {
        Matcher m = FIELD.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(resolve(m.group(1), m.group(2), m.group(3), txn)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String resolve(String kind, String key, String field, Txn txn) {
        switch (kind) {
            case "amount":
                return String.valueOf(txn.getAmountCents());
            case "from":
                return txn.getFromAccountId();
            case "account": {
                Account a = key == null ? null : store.getAccountById(key);
                if (a == null) {
                    return "";
                }
                return switch (field) {
                    case "balance" -> String.valueOf(a.getBalanceCents());
                    case "owner" -> a.getOwnerUserId();
                    case "currency" -> a.getCurrency();
                    default -> "";
                };
            }
            case "user": {
                User u = key == null ? null : store.getUserByUsername(key);
                if (u == null) {
                    return "";
                }
                return switch (field) {
                    case "email" -> String.valueOf(u.getEmail());
                    case "role" -> u.getRole();
                    case "id" -> u.getId();
                    case "displayName" -> u.getDisplayName();
                    default -> "";
                };
            }
            default:
                return "";
        }
    }
}
