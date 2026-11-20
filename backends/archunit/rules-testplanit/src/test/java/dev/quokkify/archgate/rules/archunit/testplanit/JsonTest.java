package dev.quokkify.archgate.rules.archunit.testplanit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

class JsonTest {

    @Test
    void readsScalars() {
        assertThat(Json.parse("\"text\"")).isEqualTo("text");
        assertThat(Json.parse("42")).isEqualTo(42L);
        assertThat(Json.parse("-7")).isEqualTo(-7L);
        assertThat(Json.parse("1.5")).isEqualTo(new BigDecimal("1.5"));
        assertThat(Json.parse("2e3")).isEqualTo(new BigDecimal("2e3"));
        assertThat(Json.parse("123456789012345678901234567890"))
                .isEqualTo(new BigDecimal("123456789012345678901234567890"));
        assertThat(Json.parse("true")).isEqualTo(Boolean.TRUE);
        assertThat(Json.parse("false")).isEqualTo(Boolean.FALSE);
        assertThat(Json.parse("null")).isNull();
    }

    @Test
    void readsObjectsInSourceOrderAndKeepsNullValues() {
        Object value = Json.parse(" { \"b\" : 1 , \"a\" : null, \"c\": [] } ");

        assertThat(value).isInstanceOf(Map.class);
        Map<?, ?> object = (Map<?, ?>) value;
        assertThat(List.copyOf(object.keySet())).isEqualTo(List.of("b", "a", "c"));
        assertThat(object.containsKey("a")).isTrue();
        assertThat(object.get("a")).isNull();
        assertThat(object.get("c")).isEqualTo(List.of());
    }

    @Test
    void readsNestedStructures() {
        Object value =
                Json.parse("{\"suites\":[{\"id\":1,\"tags\":[\"x\",{\"deep\":true}]}],\"_links\":{\"next\":null}}");

        Map<?, ?> root = (Map<?, ?>) value;
        List<?> suites = (List<?>) root.get("suites");
        Map<?, ?> suite = (Map<?, ?>) suites.get(0);
        assertThat(suite.get("id")).isEqualTo(1L);
        assertThat(suite.get("tags")).isEqualTo(List.of("x", Map.of("deep", true)));
        assertThat(((Map<?, ?>) root.get("_links")).get("next")).isNull();
    }

    @Test
    void readsArraysWithNullElements() {
        assertThat((List<?>) Json.parse("[1, null, \"a\"]")).isEqualTo(Arrays.asList(1L, null, "a"));
    }

    @Test
    void decodesEscapes() {
        assertThat(Json.parse("\"q\\\" b\\\\ s\\/ \\b\\f\\n\\r\\t\"")).isEqualTo("q\" b\\ s/ \b\f\n\r\t");
        assertThat(Json.parse("\"\\u0041\\u00e9\\uD83D\\uDE00\"")).isEqualTo("A\u00e9\uD83D\uDE00");
        assertThat(Json.parse("\"caf\u00e9\"")).isEqualTo("caf\u00e9");
    }

    @DataProvider
    Object[][] malformed() {
        return new Object[][] {
            {""},
            {"   "},
            {"{"},
            {"[1,]"},
            {"{\"a\":1,}"},
            {"{\"a\" 1}"},
            {"{a:1}"},
            {"[1 2]"},
            {"\"open"},
            {"\"bad \\x escape\""},
            {"\"\\u12G4\""},
            {"\"\\u\u0661\u0662\u0663\u0664\""},
            {"\"\\u00\uFF11\uFF12\""},
            {"\"raw\ncontrol\""},
            {"01"},
            {"-"},
            {"1."},
            {"1e"},
            {"tru"},
            {"nul"},
            {"{} {}"},
            {"[1] x"},
            {"<html>"},
        };
    }

    @Test(dataProvider = "malformed")
    void rejectsMalformedInput(String text) {
        assertThatThrownBy(() -> Json.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Malformed JSON");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> Json.parse(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsExcessiveNestingWithoutStackOverflow() {
        String deep = "[".repeat(10_000) + "]".repeat(10_000);

        assertThatThrownBy(() -> Json.parse(deep))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nesting");
    }
}
