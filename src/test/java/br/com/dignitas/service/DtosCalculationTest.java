package br.com.dignitas.service;

import br.com.dignitas.service.Dtos.CategoryStat;
import br.com.dignitas.service.Dtos.Overview;
import br.com.dignitas.service.Dtos.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cálculos simples de paginação e de percentual de acerto (sem banco de dados). */
class DtosCalculationTest {

    @Test
    void pagesIsAtLeastOneEvenWhenEmpty() {
        assertEquals(1, new PageResult<String>(List.of(), 0, 0, 10).pages());
    }

    @Test
    void pagesRoundsUpPartialLastPage() {
        assertEquals(3, new PageResult<String>(List.of(), 21, 0, 10).pages());
    }

    @Test
    void pagesDoesNotAddEmptyPageWhenTotalIsExactMultiple() {
        assertEquals(2, new PageResult<String>(List.of(), 20, 0, 10).pages());
    }

    @Test
    void overviewAccuracyIsZeroWithoutAttempts() {
        assertEquals(0.0, new Overview(5, 0, 0, 0, 0, 1).accuracy());
    }

    @Test
    void overviewAccuracyIsCorrectAttemptsOverTotal() {
        assertEquals(0.75, new Overview(5, 3, 4, 3, 0, 1).accuracy(), 1e-9);
    }

    @Test
    void categoryAccuracyIsCorrectOverAttempts() {
        assertEquals(0.5, new CategoryStat("Matemática", 10, 5).accuracy(), 1e-9);
        assertEquals(0.0, new CategoryStat("Sem disciplina", 0, 0).accuracy());
    }
}
