package com.varlanv.imp;

@FunctionalInterface
interface PredicateAccumulator {

    boolean apply(boolean left, boolean right);
}
