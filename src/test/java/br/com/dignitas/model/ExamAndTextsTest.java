package br.com.dignitas.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras de nome de concurso, rótulos e utilidades de texto (sem banco de dados). */
class ExamAndTextsTest {

    @Test
    void examNameOrDefaultUsesStrippedName() {
        assertEquals("Concurso", new ExamInfo("  Concurso  ", null, null, null).nameOrDefault());
    }

    @Test
    void examNameOrDefaultBuildsFromBoardAndYearWhenNameIsMissing() {
        assertEquals("Cespe 2024", new ExamInfo(null, "Cespe", 2024, null).nameOrDefault());
    }

    @Test
    void examNameOrDefaultFallsBackWhenEverythingIsMissing() {
        assertEquals("Sem concurso", new ExamInfo(null, null, null, null).nameOrDefault());
    }

    @Test
    void examInfoIsEmptyIgnoresRole() {
        assertTrue(new ExamInfo(null, null, null, "Analista").isEmpty());
        assertFalse(new ExamInfo("TRF", null, null, null).isEmpty());
    }

    @Test
    void examLabelJoinsBoardYearAndNameInOrder() {
        assertEquals("Cespe · 2024 · TRF", ExamLabels.of("Cespe", 2024, "TRF"));
    }

    @Test
    void examLabelSkipsBlankPartsAndShowsDashWhenEmpty() {
        assertEquals("X", ExamLabels.of(" ", null, "X"));
        assertEquals("—", ExamLabels.of(null, null, null));
    }

    @Test
    void normalizeRemovesAccentsCaseAndPunctuation() {
        assertEquals("acaootima", Texts.normalize("Ação, Ótima!"));
    }

    @Test
    void normalizeOfNullIsEmpty() {
        assertEquals("", Texts.normalize(null));
    }

    @Test
    void abbreviateCollapsesWhitespace() {
        assertEquals("a b c", Texts.abbreviate("a   b \n c", 10));
    }

    @Test
    void abbreviateTruncatesWithEllipsisWithinLimit() {
        String result = Texts.abbreviate("abcdefghijk", 5);
        assertEquals("abcd…", result);
        assertEquals(5, result.length());
    }

    @Test
    void blankToNullReturnsNullForBlankAndStrippedValueOtherwise() {
        assertNull(Texts.blankToNull("   "));
        assertEquals("x", Texts.blankToNull("  x "));
    }
}
