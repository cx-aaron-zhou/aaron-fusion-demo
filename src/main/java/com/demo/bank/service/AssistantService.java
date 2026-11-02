package com.demo.bank.service;

import com.demo.bank.model.Account;
import com.demo.bank.model.Txn;
import com.demo.bank.model.User;
import com.demo.bank.store.DataStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer-support assistant. It is given the customer's question plus their recent
 * account activity as context and answers by calling banking tools on their behalf.
 *
 * <p>The model call is stubbed with a deterministic planner so the demo needs no API
 * key: any line of the prompt that reads {@code TOOL: <name> <args...>} is executed.
 */
public class AssistantService {

    private static final String SYSTEM_PROMPT =
            "SYSTEM: You are the bank's support assistant. Help the customer using these tools: "
                    + "get_balance <account>, transfer <from> <to> <cents>, set_role <username> <role>.";

    private final DataStore store;
    private final AccountService accounts;

    public AssistantService(DataStore store, AccountService accounts) {
        this.store = store;
        this.accounts = accounts;
    }

    public List<String> ask(User caller, String question) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT).append('\n');
        prompt.append("USER (").append(caller.getUsername()).append("): ").append(question).append('\n');
        prompt.append("CONTEXT - recent activity on the customer's accounts:\n");
        for (Account account : store.allAccounts()) {
            if (!account.getOwnerUserId().equals(caller.getId())) {
                continue;
            }
            List<Txn> history = store.txnsFor(account.getId());
            for (Txn txn : history.subList(Math.max(0, history.size() - 10), history.size())) {
                prompt.append("- ").append(txn.getMemo()).append('\n');
            }
        }
        List<String> results = new ArrayList<>();
        for (String call : plan(prompt.toString())) {
            results.add(run(call.split("\\s+")));
        }
        return results;
    }

    /** Stand-in for the model: returns the tool calls it decided to make. */
    private static List<String> plan(String prompt) {
        List<String> calls = new ArrayList<>();
        for (String line : prompt.split("\n")) {
            String text = line.trim();
            if (text.startsWith("- ")) {
                text = text.substring(2).trim();
            }
            if (text.startsWith("TOOL:")) {
                calls.add(text.substring("TOOL:".length()).trim());
            }
        }
        return calls;
    }

    private String run(String[] call) {
        try {
            return switch (call[0]) {
                case "get_balance" -> {
                    Account a = store.getAccountById(call[1]);
                    yield a == null ? "no such account" : a.getId() + " balance " + a.getBalanceCents();
                }
                case "transfer" -> accounts.transfer(call[1], call[2], Long.parseLong(call[3]), "assistant")
                        ? "transferred " + call[3] + " from " + call[1] + " to " + call[2]
                        : "transfer failed";
                case "set_role" -> {
                    User u = store.getUserByUsername(call[1]);
                    if (u == null) {
                        yield "no such user";
                    }
                    u.setRole(call[2]);
                    yield call[1] + " is now " + call[2];
                }
                default -> "unknown tool " + call[0];
            };
        } catch (RuntimeException e) {
            return "bad tool call: " + String.join(" ", call);
        }
    }
}
