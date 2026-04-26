package com.evalvis.database;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLineCodecTest {

    @Test
    void encodeIncludesVersion() {
        String encoded = JsonLineCodec.encode("key1", "val1", 42L);
        assertTrue(encoded.contains("\"version\":42"));
    }

    @Test
    void decodeExtractsVersion() {
        String line = "{\"key\":\"key1\",\"value\":\"val1\",\"version\":42}";
        Optional<JsonLineRecord> record = JsonLineCodec.decode(line);
        assertTrue(record.isPresent());
        assertEquals(42L, record.get().version());
    }

    @Test
    void decodeHandlesOldFormatWithoutVersion() {
        String line = "{\"key\":\"key1\",\"value\":\"val1\"}";
        Optional<JsonLineRecord> record = JsonLineCodec.decode(line);
        assertTrue(record.isPresent());
        assertEquals(0L, record.get().version());
    }
}
