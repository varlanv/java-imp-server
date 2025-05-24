package com.varlanv.imp;

import java.net.URI;
import org.intellij.lang.annotations.Language;

public final class ImpUrlMatch {

    private final String urlString;

    ImpUrlMatch(URI requestedUri) {
        this.urlString = requestedUri.toString();
    }

    public boolean urlMatches(@Language("regexp") String pattern) {
        Preconditions.nonNull(pattern, "pattern");
        return urlString.matches(pattern);
    }

    public boolean urlContains(String substring) {
        Preconditions.nonNull(substring, "substring");
        return urlString.contains(substring);
    }

    public boolean urlContainsIgnoreCase(String substring) {
        Preconditions.nonNull(substring, "substring");
        return urlString.toLowerCase().contains(substring.toLowerCase());
    }
}
