package com.varlanv.imp;

@FunctionalInterface
public interface ImpRunnable extends Runnable {

    @Override
    default void run() {
        try {
            unsafeRun();
        } catch (Exception e) {
            InternalUtils.hide(e);
        }
    }

    void unsafeRun() throws Exception;
}
