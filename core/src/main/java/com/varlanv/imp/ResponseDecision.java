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
            try {
                if (candidate.requestPredicate().test(exchange)) {
                    return candidate;
                }
            } catch (Exception e) {
                ImpLog.error(e);
                var matcherId = candidate.id();
                throw new RuntimeException(
                        String.format(
                                "Exception was thrown by request predicate with id [%s]. Please check your ImpServer configuration for [%s] request matcher. "
                                        + "Thrown error is [%s]: %s",
                                matcherId, matcherId, e.getClass().getName(), e.getMessage()),
                        e);
            }
        }
        return null;
    }
}
