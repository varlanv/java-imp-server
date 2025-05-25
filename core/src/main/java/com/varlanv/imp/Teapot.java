package com.varlanv.imp;

import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
        ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFn =
                ignored -> () -> new ByteArrayInputStream(String.format(
                                "No matching handler for request. Returning 418 [I'm a teapot]. "
                                        + "Available matcher IDs: %s",
                                matchersId)
                        .getBytes(StandardCharsets.UTF_8));
        return ImpResponse.builder()
                .trustedStatus(ImpHttpStatus.I_AM_A_TEAPOT)
                .bodyFromRequest(bodyFn)
                .trustedHeaders(headers -> {
                    var h = new HashMap<>(headers);
                    h.put("Content-Type", List.of(ImpContentType.PLAIN_TEXT.stringValue()));
                    return Collections.unmodifiableMap(h);
                })
                .build();
    }
}
