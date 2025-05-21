package com.varlanv.imp;

public final class RequestMatchBuilder {

    private ImpPredicate<ImpHeadersMatch> headersPredicate = ImpPredicate.alwaysTrue();

    public RequestMatchBuilder headersPredicate(ImpPredicate<ImpHeadersMatch> headersPredicate) {
        Preconditions.nonNull(headersPredicate, "headersPredicate");
        this.headersPredicate = headersPredicate;
        return this;
    }

    RequestMatch build() {
        return new RequestMatch(headersPredicate);
    }
}
