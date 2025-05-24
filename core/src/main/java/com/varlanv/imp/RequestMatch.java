package com.varlanv.imp;

public final class RequestMatch {

    private final ImpPredicate<ImpHeadersMatch> headersPredicate;
    private final ImpPredicate<ImpBodyMatch> bodyPredicate;

    public RequestMatch(ImpPredicate<ImpHeadersMatch> headersPredicate, ImpPredicate<ImpBodyMatch> bodyPredicate) {
        this.headersPredicate = headersPredicate;
        this.bodyPredicate = bodyPredicate;
    }

    public ImpPredicate<ImpHeadersMatch> headersPredicate() {
        return headersPredicate;
    }

    public ImpPredicate<ImpBodyMatch> bodyPredicate() {
        return bodyPredicate;
    }
}
