package com.varlanv.imp;

public final class RequestMatchBuilder {

    private final SingleAssign<ImpPredicate<ImpHeadersMatch>> headersPredicate =
            new SingleAssign<>(ImpPredicate.alwaysTrue(), "headersPredicate");

    public RequestMatchBuilder headersPredicate(ImpPredicate<ImpHeadersMatch> headersPredicate) {
        Preconditions.nonNull(headersPredicate, "headersPredicate");
        this.headersPredicate.set(headersPredicate);
        return this;
    }

    RequestMatch build() {
        return new RequestMatch(headersPredicate.get());
    }
}
