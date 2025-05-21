package com.varlanv.imp;

final class DefaultImpServer implements ImpServer {

    private final ImpServerContext context;

    DefaultImpServer(ImpServerContext context) {
        this.context = context;
    }

    @Override
    public int port() {
        return context.config().port().value();
    }

    @Override
    public ImpStatistics statistics() {
        return new ImpStatistics(context.statistics());
    }
}
