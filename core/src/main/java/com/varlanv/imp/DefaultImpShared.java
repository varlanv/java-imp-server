package com.varlanv.imp;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

final class DefaultImpShared implements ImpShared {

    private final ImpServerContext context;
    private final StartedServer startedServer;
    private final BorrowedState borrowedState;
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);

    DefaultImpShared(ImpServerContext context, StartedServer startedServer, BorrowedState borrowedState) {
        this.context = context;
        this.startedServer = startedServer;
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
        return startedServer.port();
    }

    @Override
    public void dispose() {
        startedServer.dispose();
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
            var impServer = new DefaultImpServer(port(), newContext);
            consumer.accept(impServer);
            return impServer.statistics();
        });
    }

    IntSupplier inProgressRequestCounter() {
        var counter = borrowedState.inProgressRequestCounter();
        return counter::get;
    }

    ServerConfig config() {
        return context.config();
    }
}
