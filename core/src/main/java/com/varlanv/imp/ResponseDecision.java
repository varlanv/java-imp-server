package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

final class ResponseDecision {

    private final List<ResponseCandidate> candidates;

    ResponseDecision(List<ResponseCandidate> candidates) {
        this.candidates = List.copyOf(candidates);
    }

    @Nullable ResponseCandidate pick(HttpExchange exchange) {
        var matchedCandidates = new TreeSet<>(Comparator.comparingInt(ResponseCandidate::priority));
        for (var candidate : candidates) {
            try {
                if (candidate.requestPredicate().test(exchange)) {
                    matchedCandidates.add(candidate);
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
        var size = matchedCandidates.size();
        if (size == 0) {
            return null;
        } else if (size == 1) {
            return matchedCandidates.iterator().next();
        } else {
            return matchedCandidates.first();
        }
    }
}
