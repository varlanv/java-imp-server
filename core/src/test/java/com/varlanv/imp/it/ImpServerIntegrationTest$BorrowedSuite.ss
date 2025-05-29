╔═ andcustomcontenttypestream_for_borrowed_server_should_return_requested_content_type_and_body ═╗
Response status code: 200
Response headers: {content-length=[9], content-type=[ctype], date=[<present>]}
Response body: some body

╔═ andcustomcontenttypestream_when_provided_supplier_fails_then_should_return_fallback_418_response ═╗
Response status code: 418
Response headers: {content-length=[152], content-type=[ctype], date=[<present>]}
Response body: Failed to read response body supplier, provided by `andCustomContentTypeStream` method. Message from exception thrown by provided supplier: some message

╔═ anddatastreambody_for_borrowed_server_should_return_octet_stream_body ═╗
Response status code: 200
Response headers: {content-length=[9], content-type=[application/octet-stream], date=[<present>]}
Response body: some body

╔═ anddatastreambody_when_provided_supplier_fails_then_should_return_fallback_418_response ═╗
Response status code: 418
Response headers: {content-length=[143], content-type=[application/octet-stream], date=[<present>]}
Response body: Failed to read response body supplier, provided by `andDataStreamBody` method. Message from exception thrown by provided supplier: some message

╔═ andheaders_should_overwrite_existing_headers ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[some-value3], date=[<present>], some-header=[some-value1, some-value2]}
Response body: any

╔═ andjsonbody_for_borrowed_server_should_return_json_body ═╗
Response status code: 200
Response headers: {content-length=[2], content-type=[application/json], date=[<present>]}
Response body: {}

╔═ andnoadditionalheaders_should_not_change_existing_headers ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ andxmlbody_for_borrowed_server_should_return_xml_body ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[application/xml], date=[<present>]}
Response body: <root></root>

╔═ should_be_able_to_borrow_server_from_shared_server_that_was_started_on_specific_port ═╗
Response status code: 400
Response headers: {content-length=[12], content-type=[text/plain], date=[<present>]}
Response body: changed text

╔═ should_return_to_original_state_after_borrowing_closure_ends ═╗
Response status code: 200
Response headers: {content-length=[9], content-type=[text/plain], date=[<present>]}
Response body: some text

╔═ [end of file] ═╗
