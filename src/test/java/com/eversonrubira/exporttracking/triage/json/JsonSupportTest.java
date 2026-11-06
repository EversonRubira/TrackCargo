package com.eversonrubira.exporttracking.triage.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// T06/T08: o JSON da resposta e "so o objeto"; texto depois dele nao passa.
class JsonSupportTest {

    @Test
    void readsObjectsListsAndScalars() {
        assertEquals(Map.of("a", 1), JsonSupport.read("{\"a\":1}"));
        assertEquals(List.of(1, 2), JsonSupport.read(" [1, 2] \n"));
        assertEquals("x", JsonSupport.read("\"x\""));
        assertEquals(42, JsonSupport.read("42"));
    }

    @Test
    void writeThenReadRoundTrips() {
        Map<String, Object> value = Map.of("k", List.of("a", "b\"quoted\"", "line\nbreak"));
        assertEquals(value, JsonSupport.read(JsonSupport.write(value)));
    }

    @Test
    void textAfterTheValueIsRejected() {
        for (String bad : List.of("{\"a\":1} thanks!", "{\"a\":1}\n{\"b\":2}", "[1] [2]", "{\"a\":1}}", "{\"a\":1}x")) {
            assertThrows(IllegalArgumentException.class, () -> JsonSupport.read(bad), bad);
        }
    }

    @Test
    void bracesAndQuotesInsideStringsDoNotConfuseTheScan() {
        assertEquals(Map.of("a", "} ] { \" tricky"), JsonSupport.read("{\"a\":\"} ] { \\\" tricky\"}  \n"));
        assertEquals(Map.of("a", "back\\slash"), JsonSupport.read("{\"a\":\"back\\\\slash\"}"));
        assertEquals(Map.of("a", List.of(Map.of("b", 1))), JsonSupport.read("{\"a\":[{\"b\":1}]}"));
    }

    @Test
    void invalidAndUnclosedJsonIsRejected() {
        for (String bad : List.of("", "not json", "{\"a\":", "{\"a\":1", "[1,", "{'a':1}")) {
            assertThrows(IllegalArgumentException.class, () -> JsonSupport.read(bad), bad);
        }
    }
}
