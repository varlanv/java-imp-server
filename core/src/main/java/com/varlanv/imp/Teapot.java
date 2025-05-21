package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

final class Teapot implements ImpFn<HttpExchange, ImpResponse> {

    private final List<ResponseCandidate> candidates;

    Teapot(List<ResponseCandidate> candidates) {
        this.candidates = candidates;
    }

    @Override
    public ImpResponse unsafeApply(HttpExchange httpExchange) {
        var matchersId = candidates.stream().map(ResponseCandidate::id).collect(Collectors.toList());
        ImpSupplier<byte[]> bodySupplier = () -> String.format(
                        "None of the matchers matched request, returning http response code [418 I'm a teapot]."
                                + " ImpServer instance contains matchers with these ids: %s",
                        matchersId)
                .getBytes(StandardCharsets.UTF_8);
        return ImmutableImpResponse.builder()
                .statusCode(ImpHttpStatus.I_AM_A_TEAPOT)
                .body(bodySupplier)
                .headers(headers -> {
                    var h = new HashMap<>(headers);
                    h.put("Content-Type", List.of(ImpContentType.PLAIN_TEXT.stringValue()));
                    return Collections.unmodifiableMap(h);
                })
                .build();
    }
}
