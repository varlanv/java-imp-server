package com.varlanv.imp;

public final class RequestMatchBuilder {

    private final SingleAssign<ImpPredicate<ImpHeadersMatch>> headersPredicate =
            new SingleAssign<>(ImpPredicate.alwaysTrue(), "headersPredicate");
    private final SingleAssign<ImpPredicate<ImpBodyMatch>> bodyPredicate =
            new SingleAssign<>(ImpPredicate.alwaysTrue(), "bodyPredicate");
    private final SingleAssign<ImpPredicate<ImpUrlMatch>> urlPredicate =
            new SingleAssign<>(ImpPredicate.alwaysTrue(), "urlPredicate");

    public RequestMatchBuilder headersPredicate(ImpPredicate<ImpHeadersMatch> headersPredicate) {
        Preconditions.nonNull(headersPredicate, "headersPredicate");
        this.headersPredicate.set(headersPredicate);
        return this;
    }

    public RequestMatchBuilder bodyPredicate(ImpPredicate<ImpBodyMatch> bodyPredicate) {
        Preconditions.nonNull(bodyPredicate, "bodyPredicate");
        this.bodyPredicate.set(bodyPredicate);
        return this;
    }

    public RequestMatchBuilder urlPredicate(ImpPredicate<ImpUrlMatch> urlPredicate) {
        Preconditions.nonNull(urlPredicate, "urlPredicate");
        this.urlPredicate.set(urlPredicate);
        return this;
    }

    RequestMatch build() {
        return new RequestMatch(headersPredicate.get(), bodyPredicate.get(), urlPredicate.get());
    }
}
