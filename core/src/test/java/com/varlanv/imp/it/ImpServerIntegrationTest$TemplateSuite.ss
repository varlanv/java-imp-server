╔═ server_should_response_with_expected_json_data ═╗
Response status code: 200
Response headers: {content-length=[19], content-type=[application/json], date=[<present>]}
Response body: {
  "key": "val"
}


╔═ should_be_able_to_build_response_based_on_request_body_with_andbodybasedonrequest ═╗
Response status code: 200
Response headers: {content-length=[12], content-type=[text/plain], date=[<present>]}
Response body: request body

╔═ should_be_able_to_start_server_at_specific_port ═╗
Response status code: 200
Response headers: {content-length=[9], content-type=[text/plain], date=[<present>]}
Response body: some text

╔═ should_be_able_to_start_server_with_matchers_on_specific_port ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_fail_when_don_t_match_by_body_predicate_bodycontains ═╗
Response status code: 418
Response headers: {content-length=[507], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Body -> contains("test") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_fail_when_fail_to_match_by_body_predicate_bodycontainsignorecase ═╗
Response status code: 418
Response headers: {content-length=[519], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Body -> containsIgnoreCase("texttt") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_fail_when_match_success_match_by_url_predicate_but_fail_to_match_by_body ═╗
Response status code: 418
Response headers: {content-length=[574], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

AND -> false
 |---> Url -> urlMatches(".*some/.*") -> true
 |---> Body -> contains("text") -> false

-----------------------------------------------------------------------------------------------------------------------------

╔═ should_fail_when_successfully_match_by_body_but_fail_to_match_by_headers ═╗
Response status code: 418
Response headers: {content-length=[599], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

AND -> false
 |---> Body -> containsIgnoreCase("text") -> true
 |---> Headers -> containsPair("header1", "header1") -> false

-----------------------------------------------------------------------------------------------------------------------------

╔═ should_fail_when_successfully_match_by_headers_but_fail_to_match_by_body ═╗
Response status code: 418
Response headers: {content-length=[602], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

AND -> false
 |---> Body -> containsIgnoreCase("texttttt") -> false
 |---> Headers -> containsPair("header1", "header1") -> N/E

-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_additional_headers_when_matched_user_agent_header_key_by_containskey_and_expect_additional_headers ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>], header1=[value1], header2=[value2, value3]}
Response body: any

╔═ should_return_error_wen_fail_to_match_by_url_predicate_urlmatches ═╗
Response status code: 418
Response headers: {content-length=[513], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Url -> urlMatches(".*local.*") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_can_t_match_by_headers_predicate_containspair ═╗
Response status code: 418
Response headers: {content-length=[528], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> containsPair("header1", "header1") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_can_t_match_by_headers_predicate_containspairlist ═╗
Response status code: 418
Response headers: {content-length=[549], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> containsPairList("header1, "[some not existing value]") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_can_t_match_by_headers_predicate_containsvalue ═╗
Response status code: 418
Response headers: {content-length=[534], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> containsValue("some not existing value") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_containsallkeys_specified_but_not_matches_requested_headers ═╗
Response status code: 418
Response headers: {content-length=[531], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> containsAllKeys("[header1, header2]") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_empty_content_type_in_request_and_containsallkeys_specified ═╗
Response status code: 418
Response headers: {content-length=[531], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> containsAllKeys("[header1, header2]") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_empty_content_type_in_request_and_containspair_specified ═╗
Response status code: 418
Response headers: {content-length=[528], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> containsPair("header1", "header1") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_empty_content_type_in_request_and_containspairlist_specified ═╗
Response status code: 418
Response headers: {content-length=[549], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> containsPairList("header1, "[some not existing value]") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_fail_to_match_by_body_predicate_bodymatches ═╗
Response status code: 418
Response headers: {content-length=[510], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Body -> matches(".*extt.*") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_fail_to_match_by_body_predicate_jsonpath_stringequals ═╗
Response status code: 418
Response headers: {content-length=[521], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

JsonPath -> $.key stringEquals("val2") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_fail_to_match_by_body_predicate_testbodystring ═╗
Response status code: 418
Response headers: {content-length=[518], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Body -> testBodyString(<predicate>) -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_fail_to_match_by_url_predicate_hasqueryparam ═╗
Response status code: 418
Response headers: {content-length=[513], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Url -> hasQueryParam("query1") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_fail_to_match_by_url_predicate_hasqueryparamkey ═╗
Response status code: 418
Response headers: {content-length=[516], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Url -> hasQueryParamKey("query3") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_fail_to_match_by_url_predicate_urlcontains_at_specific_path ═╗
Response status code: 418
Response headers: {content-length=[510], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Url -> urlContains("me/pa") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_fail_to_match_by_url_predicate_urlcontainsignorecase_at_specific_path ═╗
Response status code: 418
Response headers: {content-length=[522], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Url -> urlContainsIgnoreCase("ome/ppp") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_hascontenttype_doesn_t_match ═╗
Response status code: 418
Response headers: {content-length=[528], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> hasContentType("application/json") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_hascontenttype_specified_but_contenttype_is_null_in_request ═╗
Response status code: 418
Response headers: {content-length=[528], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Headers -> hasContentType("application/json") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_query_is_empty_but_request_to_match_by_url_predicate_hasqueryparam ═╗
Response status code: 418
Response headers: {content-length=[513], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Url -> hasQueryParam("query1") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_query_is_empty_but_request_to_match_by_url_predicate_hasqueryparamkey ═╗
Response status code: 418
Response headers: {content-length=[516], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [anyId]
Below is the list of evaluated conditions and their results:
-----------------------------------------------------------------------------------------------------------------------------
Matcher: id = anyId, priority = 0

Url -> hasQueryParamKey("query1") -> false
-----------------------------------------------------------------------------------------------------------------------------

╔═ should_return_error_when_testbodystring_predicate_throws_exception ═╗
Response status code: 418
Response headers: {content-length=[208], date=[<present>]}
Response body: Exception was thrown by request predicate with id [matcherId]. Please check your ImpServer configuration for [matcherId] request matcher. Thrown error is [java.lang.RuntimeException]: testBodyString exception

╔═ should_return_expected_response_when_matched_user_agent_header_key_by_containskey ═╗
Response status code: 200
Response headers: {content-length=[9], content-type=[text/plain], date=[<present>]}
Response body: some text

╔═ should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_custom_content_type ═╗
Response status code: 200
Response headers: {content-length=[9], content-type=[customContentType], date=[<present>]}
Response body: some-data

╔═ should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_json_body ═╗
Response status code: 200
Response headers: {content-length=[18], content-type=[application/json], date=[<present>]}
Response body: { "some": "json" }

╔═ should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_stream_body ═╗
Response status code: 200
Response headers: {content-length=[9], content-type=[application/octet-stream], date=[<present>]}
Response body: some-data

╔═ should_return_expected_response_when_matched_user_agent_header_key_by_containskey_and_expect_xml_body ═╗
Response status code: 200
Response headers: {content-length=[33], content-type=[application/xml], date=[<present>]}
Response body: <root><entry>value</entry></root>

╔═ should_return_expected_status_when_matched_user_agent_header_key_by_containskey_and_expect_specific_status ═╗
Response status code: 404
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_return_fallback_response_when_none_of_matchers_matched_request_and_expected_json_body ═╗
Response status code: 418
Response headers: {content-length=[575], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [some matcher id]
Below is the list of evaluated conditions and their results:
---------------------------------------------------------------------------------------------------------------------------------------
Matcher: id = some matcher id, priority = 0

Headers -> containsKey("unknown-not-matched-header") -> false
---------------------------------------------------------------------------------------------------------------------------------------

╔═ should_return_fallback_response_when_none_of_matchers_matched_request_and_expected_text_body ═╗
Response status code: 418
Response headers: {content-length=[575], content-type=[text/plain], date=[<present>]}
Response body: No matching handler for request. Returning status code 418 to make sure that test fails early. Available matcher IDs: [some matcher id]
Below is the list of evaluated conditions and their results:
---------------------------------------------------------------------------------------------------------------------------------------
Matcher: id = some matcher id, priority = 0

Headers -> containsKey("unknown-not-matched-header") -> false
---------------------------------------------------------------------------------------------------------------------------------------

╔═ should_return_fallback_when_hascontenttype_specified_but_contenttype_is_empty_list_in_request_and_fallback_specified ═╗
Response status code: 400
Response headers: {content-length=[8], date=[<present>]}
Response body: fallback

╔═ should_return_fallback_when_hascontenttype_specified_but_contenttype_is_null_in_request_and_fallback_specified ═╗
Response status code: 400
Response headers: {content-length=[8], date=[<present>]}
Response body: fallback

╔═ should_return_fallback_when_hascontenttype_specified_request_contains_content_type_header_but_content_type_not_matches_and_fallback_specified ═╗
Response status code: 400
Response headers: {content-length=[8], date=[<present>]}
Response body: fallback

╔═ should_return_only_exact_headers_when_matched_user_agent_header_key_by_containskey_and_expect_exact_headers ═╗
Response status code: 200
Response headers: {content-length=[3], date=[<present>], header1=[value1], header2=[value2, value3]}
Response body: any

╔═ should_successfully_match_by_body_and_headers_together ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_body_predicate_bodycontains ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_successfully_match_by_body_predicate_bodycontainsignorecase ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_successfully_match_by_body_predicate_bodymatches ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_successfully_match_by_body_predicate_jsonpath_stringequals ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_body_predicate_testbodystring ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_successfully_match_by_headers_predicate_containsallkeys ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_successfully_match_by_headers_predicate_containspair ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_successfully_match_by_headers_predicate_hascontenttype ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_successfully_match_by_url_predicate_hasqueryparam ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_url_predicate_hasqueryparamkey ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_url_predicate_hasqueryparamkey_when_query_value_is_empty_after ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_url_predicate_hasqueryparamkey_when_query_value_is_not_present ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_url_predicate_urlcontains_at_specific_path ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_url_predicate_urlcontainsignorecase_at_specific_path ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_url_predicate_urlmatches_at_root_path ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfully_match_by_url_predicate_urlmatches_at_specific_path ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_successfuly_match_by_headers_predicate_containspairlist ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_successfuly_match_by_headers_predicate_containsvalue ═╗
Response status code: 200
Response headers: {content-length=[3], content-type=[text/plain], date=[<present>]}
Response body: any

╔═ should_take_matcher_with_lowest_priority_value_when_multiple_matchers_matched_request_and_matcher_is_first_in_list ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ should_take_matcher_with_lowest_priority_value_when_multiple_matchers_matched_request_and_matcher_is_last_in_list ═╗
Response status code: 200
Response headers: {content-length=[13], content-type=[text/plain], date=[<present>]}
Response body: response body

╔═ [end of file] ═╗
