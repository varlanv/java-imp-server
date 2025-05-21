package com.varlanv.imp;

enum ImpContentType implements CharSequence {
    JSON("application/json"),
    XML("application/xml"),
    PLAIN_TEXT("text/plain"),
    OCTET_STREAM("application/octet-stream");

    private final String stringValue;

    ImpContentType(String stringValue) {
        this.stringValue = stringValue;
    }

    String stringValue() {
        return stringValue;
    }

    @Override
    public int length() {
        return stringValue.length();
    }

    @Override
    public char charAt(int index) {
        return stringValue.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return stringValue.subSequence(start, end);
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
