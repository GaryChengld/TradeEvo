package com.tradeevo.core.json;

import static com.tradeevo.TestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CanonicalJsonTest {

    @Test
    void keyOrderDoesNotChangeTheHash() {
        assertThat(CanonicalJson.sha256(json("{'b': 1, 'a': {'y': [1, {'q': 1, 'p': 2}], 'x': null}}")))
                .isEqualTo(CanonicalJson.sha256(json("{'a': {'x': null, 'y': [1, {'p': 2, 'q': 1}]}, 'b': 1}")));
        assertThat(CanonicalJson.toCanonicalString(json("{'b': 1, 'a': 2}"))).isEqualTo("{\"a\":2,\"b\":1}");
    }

    @Test
    void valuesAndArrayOrderDoChangeTheHash() {
        String base = CanonicalJson.sha256(json("{'a': [1, 2]}"));
        assertThat(CanonicalJson.sha256(json("{'a': [2, 1]}"))).isNotEqualTo(base);
        assertThat(CanonicalJson.sha256(json("{'a': [1, 3]}"))).isNotEqualTo(base);
        assertThat(base).hasSize(64);
    }

    @Test
    void copyIsDeep() {
        var original = json("{'a': {'b': 1}}");
        var copy = CanonicalJson.copy(original);
        ((com.fasterxml.jackson.databind.node.ObjectNode) copy.get("a")).put("b", 2);

        assertThat(original.at("/a/b").intValue()).isEqualTo(1);
        assertThat(CanonicalJson.copy(null)).isNull();
    }
}
