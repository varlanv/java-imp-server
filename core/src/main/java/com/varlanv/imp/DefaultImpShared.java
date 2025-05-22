package com.varlanv.imp;

import java.util.concurrent.atomic.AtomicBoolean;

final class DefaultImpShared implements ImpShared {

    private final ImpServerContext context;
    private final Disposable httpServer;
    private final BorrowedState borrowedState;
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);

    DefaultImpShared(ImpServerContext context, Disposable httpServer, BorrowedState borrowedState) {
        this.context = context;
        this.httpServer = httpServer;
        this.borrowedState = borrowedState;
    }

    @Override
    public ImpBorrowedSpec borrow() {
        if (isDisposed()) {
            throw new IllegalStateException("Cannot borrow from already stopped server");
        }
        return new ImpBorrowedSpec(this);
    }

    @Override
    public int port() {
        return context.config().port().value();
    }

    @Override
    public void dispose() {
        httpServer.dispose();
        isDisposed.set(true);
    }

    @Override
    public boolean isDisposed() {
        return isDisposed.get();
    }

    @Override
    public ImpStatistics statistics() {
        return new ImpStatistics(context.statistics());
    }

    ImpStatistics useWithMutatedContext(ServerConfig config, ImpConsumer<ImpServer> consumer) {
        var newContext = new ImpServerContext(config, new MutableImpStatistics());
        return borrowedState.doWithLockedContext(newContext, () -> {
            var impServer = new DefaultImpServer(newContext);
            consumer.accept(impServer);
            return impServer.statistics();
        });
    }

    ServerConfig config() {
        return context.config();
    }
}
