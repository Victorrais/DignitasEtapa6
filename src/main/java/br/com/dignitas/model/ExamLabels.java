package br.com.dignitas.model;

/** Rótulo de um concurso para listas e tabelas, por exemplo "Cespe · 2024 · TRF". */
public final class ExamLabels {

    private ExamLabels() {
    }

    public static String of(String board, Integer year, String name) {
        StringBuilder sb = new StringBuilder();
        if (board != null && !board.isBlank()) sb.append(board);
        if (year != null) sb.append(sb.isEmpty() ? "" : " · ").append(year);
        if (name != null && !name.isBlank()) sb.append(sb.isEmpty() ? "" : " · ").append(name);
        return sb.isEmpty() ? "—" : sb.toString();
    }
}
