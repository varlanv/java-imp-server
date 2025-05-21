package com.varlanv.imp;

public final class RequestMatch {

    private final ImpPredicate<ImpHeadersMatch> headersPredicate;

    public RequestMatch(ImpPredicate<ImpHeadersMatch> headersPredicate) {
        this.headersPredicate = headersPredicate;
    }

    public ImpPredicate<ImpHeadersMatch> headersPredicate() {
        return headersPredicate;
    }
}
