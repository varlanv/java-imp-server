package com.varlanv.imp;

public interface ImpShared {

    int port();

    void dispose();

    boolean isDisposed();

    ImpStatistics statistics();

    ImpBorrowedSpec borrow();
}
