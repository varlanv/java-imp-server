package com.varlanv.imp;

public final class RequestMatch {

    private final ImpPredicate<ImpHeadersMatch> headersPredicate;
    private final ImpPredicate<ImpBodyMatch> bodyPredicate;
    private final ImpPredicate<ImpUrlMatch> urlPredicate;

    RequestMatch(
            ImpPredicate<ImpHeadersMatch> headersPredicate,
            ImpPredicate<ImpBodyMatch> bodyPredicate,
            ImpPredicate<ImpUrlMatch> urlPredicate) {
        this.headersPredicate = headersPredicate;
        this.bodyPredicate = bodyPredicate;
        this.urlPredicate = urlPredicate;
    }

    public ImpPredicate<ImpHeadersMatch> headersPredicate() {
        return headersPredicate;
    }

    public ImpPredicate<ImpBodyMatch> bodyPredicate() {
        return bodyPredicate;
    }

    public ImpPredicate<ImpUrlMatch> urlPredicate() {
        return urlPredicate;
    }
}
