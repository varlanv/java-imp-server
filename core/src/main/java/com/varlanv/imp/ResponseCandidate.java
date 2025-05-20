package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;

final class ResponseCandidate {

    private final ImpPredicate<HttpExchange> requestPredicate;
    private final ImpSupplier<ImpResponse> responseSupplier;

    ResponseCandidate(ImpPredicate<HttpExchange> requestPredicate, ImpSupplier<ImpResponse> responseSupplier) {
        this.requestPredicate = requestPredicate;
        this.responseSupplier = responseSupplier;
    }

    ImpPredicate<HttpExchange> requestPredicate() {
        return requestPredicate;
    }

    ImpSupplier<ImpResponse> responseSupplier() {
        return responseSupplier;
    }
}
