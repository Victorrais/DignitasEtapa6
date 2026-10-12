package br.com.dignitas.service;

import br.com.dignitas.model.ExamLabels;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Objetos de transferência usados pelas telas (somente leitura). */
public final class Dtos {
    private Dtos() {
    }

    public record PageResult<T>(List<T> items, long total, int page, int size) {
        public int pages() {
            return (int) Math.max(1, (total + size - 1) / size);
        }
    }

    public enum Status {
        ALL("Todas"), UNANSWERED("Não respondidas"), WRONG("Que já errei"), FLAGGED("Marcadas para revisão");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Sort {
        RECENT("Mais recentes"), OLDEST("Mais antigas"), NUMBER("Nº da questão");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record QuestionFilter(String text, Long subjectId, String board, Integer year, Long examId,
                                 Status status, Sort sort, boolean skipAnnulled) {
        public static QuestionFilter all() {
            return new QuestionFilter(null, null, null, null, null, Status.ALL, Sort.RECENT, false);
        }
    }

    public record QuestionRow(long id, Integer number, String statement, String subject,
                              String board, Integer year, String examName,
                              LocalDateTime updatedAt, boolean annulled, boolean flagged) {
        public String origin() {
            return ExamLabels.of(board, year, examName);
        }
    }

    public enum ResultFilter {
        ALL("Todas"), CORRECT("Corretas"), WRONG("Erradas");

        private final String label;

        ResultFilter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record HistoryRow(long attemptId, long questionId, Integer number, String statement,
                             String board, Integer year, String examName,
                             String chosenLetter, boolean correct, LocalDateTime answeredAt) {
        public String origin() {
            return ExamLabels.of(board, year, examName);
        }
    }

    public record AnswerResult(boolean correct, String correctLetter) {
    }

    public record Overview(long questions, long answeredQuestions, long attempts, long correctAttempts,
                           long flagged, long imports) {
        public double accuracy() {
            return attempts == 0 ? 0 : (double) correctAttempts / attempts;
        }
    }

    public record CategoryStat(String name, long attempts, long correct) {
        public double accuracy() {
            return attempts == 0 ? 0 : (double) correct / attempts;
        }
    }

    public record DailyStat(LocalDate day, long attempts, long correct) {
    }

    public record ImportRow(long id, String fileName, String board, Integer year, String examName,
                            LocalDateTime importedAt, int pageCount, int ocrPages, long questions) {
        public String origin() {
            return ExamLabels.of(board, year, examName);
        }
    }

    public record SaveSummary(int saved, int skippedDuplicates) {
    }
}
