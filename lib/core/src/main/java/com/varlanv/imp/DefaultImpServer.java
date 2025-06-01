package com.varlanv.imp;

final class DefaultImpServer implements ImpServer {

    private final int port;
    private final ImpServerContext context;

    DefaultImpServer(int port, ImpServerContext context) {
        this.port = port;
        this.context = context;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public ImpStatistics statistics() {
        return new ImpStatistics(context.statistics());
    }
}
