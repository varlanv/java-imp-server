package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;

final class ResponseCandidate {

    static final String DEFAULT_ID = "__default_imp_request_handler__";
    private final String id;
    private final int priority;
    private final ImpPredicate<HttpExchange> requestPredicate;
    private final ImpSupplier<ImpResponse> responseSupplier;

    ResponseCandidate(
            String id,
            int priority,
            ImpPredicate<HttpExchange> requestPredicate,
            ImpSupplier<ImpResponse> responseSupplier) {
        this.id = id;
        this.priority = priority;
        this.requestPredicate = requestPredicate;
        this.responseSupplier = responseSupplier;
    }

    ResponseCandidate(ImpPredicate<HttpExchange> requestPredicate, ImpSupplier<ImpResponse> responseSupplier) {
        this(DEFAULT_ID, 0, requestPredicate, responseSupplier);
    }

    ImpPredicate<HttpExchange> requestPredicate() {
        return requestPredicate;
    }

    ImpSupplier<ImpResponse> responseSupplier() {
        return responseSupplier;
    }

    public String id() {
        return id;
    }

    public int priority() {
        return priority;
    }
}
