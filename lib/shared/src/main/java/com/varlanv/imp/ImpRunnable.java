package com.varlanv.imp;

@FunctionalInterface
public interface ImpRunnable {

    default void run() {
        try {
            unsafeRun();
        } catch (Exception e) {
            InternalUtils.hide(e);
        }
    }

    void unsafeRun() throws Exception;
}
