package br.com.dignitas.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras de validação e de detecção de duplicidade do rascunho de questão (sem banco de dados). */
class QuestionDraftRulesTest {

    /** Rascunho válido: enunciado, cinco alternativas e uma correta (C). */
    private static QuestionDraft valid(String statement) {
        QuestionDraft d = new QuestionDraft();
        d.setStatement(statement);
        String[] letters = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < letters.length; i++) {
            d.addAlternative(letters[i], "texto " + letters[i], "C".equals(letters[i]));
        }
        return d;
    }

    @Test
    void validDraftHasNoErrors() {
        assertTrue(valid("Quanto é 2 + 2?").validate().isEmpty());
    }

    @Test
    void emptyDraftReportsMissingStatementAndTooFewAlternatives() {
        List<String> errors = new QuestionDraft().validate();
        assertTrue(errors.contains("O enunciado está vazio."), errors::toString);
        assertTrue(errors.contains("A questão precisa de pelo menos 2 alternativas."), errors::toString);
    }

    @Test
    void draftWithoutCorrectAlternativeIsRejected() {
        QuestionDraft d = valid("Enunciado");
        d.getAlternatives().forEach(a -> a.setCorrect(false));
        assertTrue(d.validate().stream().anyMatch(e -> e.startsWith("Marque exatamente uma")));
    }

    @Test
    void draftWithTwoCorrectAlternativesIsRejected() {
        QuestionDraft d = valid("Enunciado");
        d.getAlternatives().get(0).setCorrect(true);
        assertTrue(d.validate().stream().anyMatch(e -> e.startsWith("Marque exatamente uma")));
    }

    @Test
    void annulledQuestionMayHaveNoCorrectAlternative() {
        QuestionDraft d = valid("Enunciado");
        d.getAlternatives().forEach(a -> a.setCorrect(false));
        d.setAnnulled(true);
        assertTrue(d.validate().isEmpty());
    }

    @Test
    void emptyAlternativeIsReportedByLetter() {
        QuestionDraft d = valid("Enunciado");
        d.getAlternatives().get(1).setText("   ");
        assertTrue(d.validate().contains("A alternativa B está vazia."));
    }

    @Test
    void contentHashIgnoresCaseAccentsAndPunctuation() {
        assertEquals(
                valid("Qual é a capital do Brasil?").contentHash(),
                valid("QUAL E A CAPITAL DO BRASIL").contentHash());
    }

    @Test
    void contentHashChangesWhenStatementChanges() {
        assertNotEquals(
                valid("Qual é a capital do Brasil?").contentHash(),
                valid("Qual é a capital da França?").contentHash());
    }

    @Test
    void contentHashIsStableForEqualContent() {
        assertFalse(valid("Mesmo enunciado").contentHash().isBlank());
        assertEquals(valid("Mesmo enunciado").contentHash(), valid("Mesmo enunciado").contentHash());
    }
}
