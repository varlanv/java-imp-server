package com.varlanv.gradle.plugin;

/**
 * Simple utility for running different configuration base on local/CI/functional-test environments
 */
final class InternalEnvironment {

    private final boolean isCi;
    private final boolean isTest;

    public InternalEnvironment(boolean isCi, boolean isTest) {
        this.isCi = isCi;
        this.isTest = isTest;
    }

    public static String name() {
        return "__internal_environment__";
    }

    public boolean isCi() {
        return isCi;
    }

    public boolean isLocal() {
        return !isCi;
    }

    public boolean isTest() {
        return isTest;
    }
}
