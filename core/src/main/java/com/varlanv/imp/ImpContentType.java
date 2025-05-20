package com.varlanv.imp;

enum ImpContentType {
    APPLICATION_JSON("application/json"),
    APPLICATION_XML("application/xml"),
    TEXT_PLAIN("text/plain");

    private final String stringValue;

    ImpContentType(String stringValue) {
        this.stringValue = stringValue;
    }

    public String stringValue() {
        return stringValue;
    }
}
