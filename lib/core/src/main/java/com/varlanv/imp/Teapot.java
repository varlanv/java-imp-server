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
        ImpFn<ImpRequestView, ImpSupplier<InputStream>> bodyFn = requestView -> () -> {
            var messageBuilder = new StringBuilder();
            var iterator = candidates.iterator();
            var firstLine =
                    "No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: "
                            + matchersId;
            var separator = "-".repeat(firstLine.length());
            var smallSeparator = "-".repeat(firstLine.length() / 2);
            while (iterator.hasNext()) {
                var candidate = iterator.next();
                var evaluated = candidate.condition().toEvaluated(requestView);
                messageBuilder
                        .append("Matcher: id = ")
                        .append(candidate.id())
                        .append(", priority = ")
                        .append(candidate.priority())
                        .append("\n\n");
                messageBuilder.append(evaluated.message.joinToBuilder());
                if (iterator.hasNext()) {
                    messageBuilder.append("\n").append(smallSeparator).append("\n");
                }
            }
            var secondLine = "Below is the list of evaluated conditions and their results:";
            var fourthLine = messageBuilder.toString();
            var finalMessage = firstLine + "\n" + secondLine + "\n" + separator + "\n" + fourthLine + "\n" + separator;
            return new ByteArrayInputStream(finalMessage.getBytes(StandardCharsets.UTF_8));
        };
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
