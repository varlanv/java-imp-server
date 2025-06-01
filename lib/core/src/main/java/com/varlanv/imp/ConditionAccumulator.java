package com.varlanv.imp;

@FunctionalInterface
interface ConditionAccumulator {

    boolean apply(boolean left, boolean right);
}
