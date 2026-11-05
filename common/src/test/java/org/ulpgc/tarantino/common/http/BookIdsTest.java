package org.ulpgc.tarantino.common.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ulpgc.tarantino.common.model.ProblemCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookIdsTest {

    @Test
    void readsAPositiveIntegerWithinAnInt32() {
        assertEquals(1342, BookIds.parse("1342"));
        assertEquals(Integer.MAX_VALUE, BookIds.parse("2147483647"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "01", "2147483648", "99999999999", "1e3", "abc", "", " 1342"})
    void rejectsAnythingElseNamingTheBookId(String value) {
        ProblemException problem = assertThrows(ProblemException.class, () -> BookIds.parse(value));

        assertEquals(ProblemCode.INVALID_PARAMETER, problem.code());
        assertEquals("book_id", problem.parameter());
    }
}
