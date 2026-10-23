package org.ulpgc.tarantino.common.language;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageCodesTest {

    @Test
    void translatesAKnownNameToItsCode() {
        assertEquals(Optional.of("en"), LanguageCodes.code("English"));
        assertEquals(Optional.of("cy"), LanguageCodes.code("Welsh"));
    }

    @Test
    void leavesANameWithoutAnIsoCodeUntranslated() {
        assertEquals(Optional.empty(), LanguageCodes.code("Middle English"));
    }

    @Test
    void leavesAnUnknownNameUntranslated() {
        assertEquals(Optional.empty(), LanguageCodes.code("Klingon"));
    }

    @Test
    void givesEachCodeItsName() {
        assertEquals(Optional.of("English"), LanguageCodes.name("en"));
        assertEquals(Optional.of("German"), LanguageCodes.name("de"));
        assertEquals(Optional.empty(), LanguageCodes.name("xx"));
    }
}
