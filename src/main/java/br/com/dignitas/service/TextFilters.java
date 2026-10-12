package br.com.dignitas.service;

import br.com.dignitas.model.Texts;

/** Trechos de JPQL e valores de busca compartilhados pelas consultas de questões e de respostas. */
final class TextFilters {
    static final String STATEMENT_CONTAINS = "lower(q.statement) like :text";

    private TextFilters() {
    }

    /** Padrão {@code like} case-insensitive; {@code text} não pode ser vazio. */
    static String containsPattern(String text) {
        return "%" + text.strip().toLowerCase() + "%";
    }

    static boolean hasText(String text) {
        return !Texts.isBlank(text);
    }
}
