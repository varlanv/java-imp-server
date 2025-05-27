package com.varlanv.imp;

import static org.assertj.core.api.Assertions.assertThat;

import com.varlanv.imp.commontest.BaseTest;
import com.varlanv.imp.commontest.FastTest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ImpMatchTest implements FastTest {

    private static final Map<String, List<String>> headers =
            Map.of("key1", List.of("value1"), "key2", List.of("value2", "value3"), "key3", List.of("value4"));

    @Nested
    class HeadersSuite implements FastTest {

        @Test
        @DisplayName("should not contain unknown header")
        void should_not_contain_unknown_header() {
            var subject = new ImpMatch().headers().containsKey("unknown");

            assertThat(subject.test(requestWithHeaders(headers))).isFalse();
        }

        @Test
        @DisplayName("should contain known headers")
        void should_contain_known_headers() {
            assertThat(new ImpMatch().headers().containsKey("key1").test(requestWithHeaders(headers)))
                    .isTrue();
            assertThat(new ImpMatch().headers().containsKey("key2").test(requestWithHeaders(headers)))
                    .isTrue();
            assertThat(new ImpMatch().headers().containsKey("key3").test(requestWithHeaders(headers)))
                    .isTrue();
        }

        @Test
        @DisplayName("combined by `and` if one matches and other not - then return false")
        void combined_by_and_if_one_matches_and_other_not_then_return_false() {
            var match = new ImpMatch();

            var subject = match.and(
                    match.headers().containsKey("key1"), match.headers().containsKey("unknown"));
            assertThat(subject.test(requestWithHeaders(headers))).isFalse();
        }

        @Test
        @DisplayName("combined by `and` if two and all matches then - then return true")
        void combined_by_and_if_two_and_all_matches_then_then_return_true() {
            var match = new ImpMatch();

            var subject = match.and(
                    match.headers().containsKey("key1"), match.headers().containsKey("key2"));
            assertThat(subject.test(requestWithHeaders(headers))).isTrue();
        }

        @Test
        @DisplayName("combined by `and` if two and all not matches then - then return false")
        void combined_by_and_if_two_and_all_not_matches_then_then_return_false() {
            var match = new ImpMatch();

            var subject = match.and(
                    match.headers().containsKey("unknown1"), match.headers().containsKey("unknown2"));
            assertThat(subject.test(requestWithHeaders(headers))).isFalse();
        }

        @Test
        @DisplayName("combined by `and` if three and all matches then - then return true")
        void combined_by_and_if_three_and_all_matches_then_then_return_true() {
            var match = new ImpMatch();

            var subject = match.and(
                    match.headers().containsKey("key1"),
                    match.headers().containsKey("key2"),
                    match.headers().containsKey("key3"));
            assertThat(subject.test(requestWithHeaders(headers))).isTrue();
        }

        @Test
        @DisplayName("combined by `or` if two and all matches then - then return true")
        void combined_by_or_if_two_and_all_matches_then_then_return_true() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("key1"), match.headers().containsKey("key2"));
            assertThat(subject.test(requestWithHeaders(headers))).isTrue();
        }

        @Test
        @DisplayName("combined by `or` if three and all matches then - then return true")
        void combined_by_or_if_three_and_all_matches_then_then_return_true() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("key1"),
                    match.headers().containsKey("key2"),
                    match.headers().containsKey("key3"));
            assertThat(subject.test(requestWithHeaders(headers))).isTrue();
        }

        @Test
        @DisplayName("combined by `or` if three, one matches and two not matches then - then return true")
        void combined_by_or_if_three_one_matches_and_two_not_matches_then_then_return_true() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("key1"),
                    match.headers().containsKey("unknown2"),
                    match.headers().containsKey("unknown3"));

            assertThat(subject.test(requestWithHeaders(headers))).isTrue();
        }

        @Test
        @DisplayName("combined by `or` if two, one matches not matches then - then return true")
        void combined_by_or_if_two_one_matches_not_matches_then_then_return_true() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("key1"), match.headers().containsKey("unknown2"));

            assertThat(subject.test(requestWithHeaders(headers))).isTrue();
        }

        @Test
        @DisplayName("combined by `or` if one matches then - then return true")
        void combined_by_or_if_one_matches_then_then_return_true() {
            var match = new ImpMatch();

            var subject = match.or(match.headers().containsKey("key1"));
            assertThat(subject.test(requestWithHeaders(headers))).isTrue();
        }

        @Test
        @DisplayName("combined by `or` if one not matches then - then return false")
        void combined_by_or_if_one_not_matches_then_then_return_false() {
            var match = new ImpMatch();

            var subject = match.or(match.headers().containsKey("unknown"));
            assertThat(subject.test(requestWithHeaders(headers))).isFalse();
        }

        @Test
        @DisplayName("combined by `or` if two and all not matches then - then return false")
        void combined_by_or_if_two_and_all_not_matches_then_then_return_false() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("unknown1"), match.headers().containsKey("unknown2"));
            assertThat(subject.test(requestWithHeaders(headers))).isFalse();
        }

        @Test
        @DisplayName("combined by `or` if three and all not matches then - then return false")
        void combined_by_or_if_three_and_all_not_matches_then_then_return_false() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("unknown1"),
                    match.headers().containsKey("unknown2"),
                    match.headers().containsKey("unknown3"));

            assertThat(subject.test(requestWithHeaders(headers))).isFalse();
        }

        @Test
        @DisplayName(
                "combined by `or` and nested `and` if three and nested `and` one not matches then - then return false")
        void combined_by_or_and_nested_and_if_three_and_nested_and_one_not_matches_then_then_return_false() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("unknown1"),
                    match.headers().containsKey("unknown2"),
                    match.or(
                            match.headers().containsKey("unknown3"),
                            match.headers().containsKey("unknown4")));

            assertThat(subject.test(requestWithHeaders(headers))).isFalse();
        }

        @Test
        @DisplayName(
                "combined by `or` and nested `and` if three and nested `and` both not matches then - then return false")
        void combined_by_or_and_nested_and_if_three_and_nested_and_both_not_matches_then_then_return_false() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("unknown1"),
                    match.headers().containsKey("unknown2"),
                    match.or(
                            match.headers().containsKey("unknown3"),
                            match.headers().containsKey("unknown4")));

            assertThat(subject.test(requestWithHeaders(headers))).isFalse();
        }

        @Test
        @DisplayName("combined by `or` and nested `and` if three and nested `and` matches then - then return true")
        void combined_by_or_and_nested_and_if_three_and_nested_and_matches_then_then_return_true() {
            var match = new ImpMatch();

            var subject = match.or(
                    match.headers().containsKey("unknown1"),
                    match.headers().containsKey("unknown2"),
                    match.or(
                            match.headers().containsKey("key1"), match.headers().containsKey("key2")));

            assertThat(subject.test(requestWithHeaders(headers))).isTrue();
        }
    }

    private ImpRequestView requestWithHeaders(Map<String, List<String>> headers) {
        try {
            return new ImpRequestView("GET", headers, () -> new byte[0], new URI(""));
        } catch (URISyntaxException e) {
            return BaseTest.hide(e);
        }
    }
}
