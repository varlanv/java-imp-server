╔═ andadditionalheaders_should_add_new_but_not_change_exiting_headers ═╗
Response status code: 200
Response headers: {another-header=[some value], content-length=[9], content-type=[text/plain], date=[<present>]}
Response body: some text

╔═ andexactheaders_should_replace_existing_headers_and_possibly_add_new ═╗
Response status code: 200
Response headers: {another-header=[some value], content-length=[9], content-type=[application/json], date=[<present>]}
Response body: some text

╔═ should_be_able_to_start_shared_server_on_specific_port_when_it_is_not_taken ═╗
Response status code: 200
Response headers: {content-length=[9], content-type=[text/plain], date=[<present>]}
Response body: some text

╔═ should_be_able_to_start_shared_server_with_matchers_on_specific_port ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ [end of file] ═╗
