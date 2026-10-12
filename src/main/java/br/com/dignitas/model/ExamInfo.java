package br.com.dignitas.model;

/** Dados do concurso digitados pelo usuário (formulário de importação ou editor). */
public record ExamInfo(String name, String board, Integer year, String role) {

    public boolean isEmpty() {
        return Texts.isBlank(name) && Texts.isBlank(board) && year == null;
    }

    /** Nome usado quando o usuário informou só banca/ano. */
    public String nameOrDefault() {
        if (!Texts.isBlank(name)) return name.strip();
        StringBuilder sb = new StringBuilder();
        if (!Texts.isBlank(board)) sb.append(board.strip());
        if (year != null) sb.append(sb.isEmpty() ? "" : " ").append(year);
        return sb.isEmpty() ? "Sem concurso" : sb.toString();
    }
}
