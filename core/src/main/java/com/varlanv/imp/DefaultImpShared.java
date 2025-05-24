package com.varlanv.imp;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;

final class DefaultImpShared implements ImpShared {

    private final ImpServerContext context;
    private final StartedServer startedServer;
    private final BorrowedState borrowedState;
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);
    private final Lock stopLock = new ReentrantLock();

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
        try {
            stopLock.lock();
            if (isDisposed.compareAndSet(false, true)) {
                startedServer.dispose();
            }
        } finally {
            stopLock.unlock();
        }
    }

    @Override
    public boolean isDisposed() {
        return isDisposed.get();
    }

    @Override
    public ImpStatistics statistics() {
        return new ImpStatistics(context.statistics());
    }

    ImpStatistics useWithMutatedContext(StartedServerConfig config, ImpConsumer<ImpServer> consumer) {
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

    StartedServerConfig config() {
        return context.config();
    }
}
