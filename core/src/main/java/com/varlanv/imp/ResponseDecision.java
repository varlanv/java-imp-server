package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class ResponseDecision {

    private final List<ResponseCandidate> candidates;

    ResponseDecision(List<ResponseCandidate> candidates) {
        this.candidates = List.copyOf(candidates);
    }

    ResponseDecision(ResponseCandidate candidates) {
        this.candidates = List.of(candidates);
    }

    @Nullable ResponseCandidate pick(HttpExchange exchange) {
        for (var candidate : candidates) {
            if (candidate.requestPredicate().test(exchange)) {
                return candidate;
            }
        }
        return null;
    }
}
