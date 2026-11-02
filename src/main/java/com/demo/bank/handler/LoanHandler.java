package com.demo.bank.handler;

import com.demo.bank.model.Loan;
import com.demo.bank.model.User;
import com.demo.bank.service.AuthService;
import com.demo.bank.service.LoanService;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/**
 * <pre>
 *   POST /api/loans                      account, amount
 *   GET  /api/loans/{id}
 *   POST /api/loans/{id}/kyc             documentRef
 *   POST /api/loans/{id}/approve         (staff)
 *   POST /api/loans/{id}/reject          (staff)
 *   POST /api/loans/{id}/disburse
 * </pre>
 */
public class LoanHandler extends BaseHandler {
    private final LoanService loans;

    public LoanHandler(AuthService auth, LoanService loans) {
        super(auth);
        this.loans = loans;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        if (method.equals("POST") && seg.length == 3) {
            Map<String, String> form = Http.parseForm(ex);
            Loan loan = loans.apply(caller, form.get("account"), amount(form));
            Http.sendJson(ex, 201, json(loan));
        } else if (method.equals("GET") && seg.length == 4) {
            Http.sendJson(ex, 200, json(loans.getOwned(seg[3], caller)));
        } else if (method.equals("POST") && seg.length == 5) {
            Loan loan = loans.get(seg[3]);
            if (loan == null) {
                Http.sendError(ex, 404, "loan not found");
                return;
            }
            switch (seg[4]) {
                case "kyc" -> loans.submitKyc(loan, caller, Http.parseForm(ex).get("documentRef"));
                case "approve" -> loans.approve(loan, caller);
                case "reject" -> loans.reject(loan, caller);
                case "disburse" -> loans.disburse(loan, caller);
                default -> {
                    notFound(ex);
                    return;
                }
            }
            Http.sendJson(ex, 200, json(loan));
        } else {
            notFound(ex);
        }
    }

    private static String json(Loan loan) {
        return "{\"id\":\"" + Http.escape(loan.getId()) + "\",\"status\":\"" + Http.escape(loan.getStatus())
                + "\",\"amountCents\":" + loan.getAmountCents() + "}";
    }
}
