package org.ulpgc.tarantino.query.benchmarking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkloadQueryTest {

    @Test
    void readsTheCategoryAndTheQueryOfALine() {
        assertEquals(new WorkloadQuery("long", "ship captain sea voyage"), WorkloadQuery.parse("long: ship captain sea voyage"));
    }

    @Test
    void rejectsALineWithoutCategory() {
        assertThrows(IllegalArgumentException.class, () -> WorkloadQuery.parse("ship captain"));
    }

    @Test
    void selectsTheQueriesOfACategoryOrAllOfThem() {
        List<WorkloadQuery> workload = List.of(new WorkloadQuery("rare", "liliput"), new WorkloadQuery("frequent", "love"));

        assertEquals(List.of("love"), QueryWorkload.texts(workload, "frequent"));
        assertEquals(List.of("liliput", "love"), QueryWorkload.texts(workload, QueryWorkload.ALL_CATEGORIES));
    }
}
