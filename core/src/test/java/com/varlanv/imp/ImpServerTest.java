package com.varlanv.imp;

import com.varlanv.imp.commontest.FastTest;
import org.junit.jupiter.api.Test;

public class ImpServerTest implements FastTest {

    @Test
    void asd(){
        ImpServer.template();
    }

//            consumeServer(server -> {
//            HttpClient httpClient = HttpClient.newHttpClient();
//            HttpRequest request = HttpRequest.newBuilder()
//                .uri(new URI(String.format("http://localhost:%d/keka/qwe/as/zx/11", server.getAddress().getPort())))
//                .GET()
//                .build();
//            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//            System.out.println(response.body());
//        });
}
