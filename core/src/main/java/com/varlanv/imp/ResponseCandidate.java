package com.varlanv.imp;

final class ResponseCandidate {

    static final String DEFAULT_ID = "__default_imp_request_handler__";
    private final String id;
    private final int priority;
    private final ImpCondition condition;
    private final ImpSupplier<ImpResponse> responseSupplier;

    ResponseCandidate(String id, int priority, ImpCondition condition, ImpSupplier<ImpResponse> responseSupplier) {
        this.id = id;
        this.priority = priority;
        this.condition = condition;
        this.responseSupplier = responseSupplier;
    }

    ResponseCandidate(ImpCondition condition, ImpSupplier<ImpResponse> responseSupplier) {
        this(DEFAULT_ID, 0, condition, responseSupplier);
    }

    ImpCondition condition() {
        return condition;
    }

    ImpSupplier<ImpResponse> responseSupplier() {
        return responseSupplier;
    }

    public String id() {
        return id;
    }

    public int priority() {
        return priority;
    }
}
