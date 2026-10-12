package br.com.dignitas.extraction;

import br.com.dignitas.model.QuestionDraft;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê gabaritos nos formatos mais comuns:
 * <ul>
 *   <li>pares na mesma linha: "01 - A   02 - C", "1. E", "3 ANULADA";</li>
 *   <li>tabela: uma linha só com números seguida de uma linha só com respostas;</li>
 *   <li>tabela com uma coluna por questão e duas colunas de resposta (ex.: ENEM, Inglês e Espanhol):
 *       "1 D B" — a segunda resposta fica em {@link AnswerKey#secondary()}.</li>
 * </ul>
 * Respostas "X", "*", "NULA" ou "ANULADA" marcam a questão como anulada.
 * Se houver mais de um gabarito (tipos de prova), vale a primeira ocorrência de cada número.
 */
public class AnswerKeyParser {
    public static final String ANNULLED = "X";

    /** Respostas principais e, quando houver, as da segunda coluna (ex.: opção Espanhol). */
    public record AnswerKey(Map<Integer, String> primary, Map<Integer, String> secondary) {
        public boolean isEmpty() {
            return primary.isEmpty();
        }

        public String get(int number) {
            return primary.get(number);
        }
    }

    private static final Pattern PAIR = Pattern.compile(
            "(?<![\\d/])(\\d{1,3})\\s*[-–—:.)]?\\s*(ANULADA|Anulada|anulada|NULA|[A-E]|X|\\*)(?![\\p{L}\\d])");
    private static final Pattern TWO_ANSWERS = Pattern.compile(
            "(?<![\\d/])(\\d{1,3})\\s+([A-E]|X|\\*)\\s+([A-E]|X|\\*)(?![\\p{L}\\d])");
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d{1,3}");
    private static final Pattern ANSWER_TOKEN = Pattern.compile("(?i)[A-E]|X|\\*|ANULADA|NULA");

    /** Agrupa as células da página em linhas visuais antes de interpretar. */
    public AnswerKey parse(List<PageContent> pages) {
        List<String> rows = new ArrayList<>();
        for (PageContent p : pages) {
            List<TextLine> cells = new ArrayList<>(p.lines().stream().filter(l -> !l.isFigure()).toList());
            cells.sort(Comparator.comparingDouble(TextLine::y).thenComparingDouble(TextLine::x));
            List<TextLine> row = new ArrayList<>();
            for (TextLine c : cells) {
                if (!row.isEmpty() && Math.abs(c.y() - row.getFirst().y()) > 3) {
                    rows.add(joinRow(row));
                    row.clear();
                }
                row.add(c);
            }
            if (!row.isEmpty()) rows.add(joinRow(row));
        }
        return parseRows(rows);
    }

    private static String joinRow(List<TextLine> row) {
        row.sort(Comparator.comparingDouble(TextLine::x));
        StringJoiner j = new StringJoiner(" ");
        row.forEach(c -> j.add(c.text().strip()));
        return j.toString();
    }

    /** Compatibilidade: só as respostas principais. */
    public Map<Integer, String> parseLines(List<String> lines) {
        return parseRows(lines).primary();
    }

    public AnswerKey parseRows(List<String> lines) {
        Map<Integer, String> key = new LinkedHashMap<>();
        Map<Integer, String> secondary = new LinkedHashMap<>();
        boolean[] used = new boolean[lines.size()];

        for (int i = 0; i + 1 < lines.size(); i++) {
            List<String> nums = tokens(lines.get(i), NUMBER_TOKEN);
            if (nums == null || nums.size() < 3) continue;
            for (int j = i + 1; j < Math.min(lines.size(), i + 3); j++) {
                List<String> answers = tokens(lines.get(j), ANSWER_TOKEN);
                if (answers != null && answers.size() == nums.size()) {
                    for (int k = 0; k < nums.size(); k++) {
                        key.putIfAbsent(Integer.parseInt(nums.get(k)), normalize(answers.get(k)));
                    }
                    used[i] = used[j] = true;
                    break;
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            if (used[i]) continue;
            String line = lines.get(i);
            Matcher two = TWO_ANSWERS.matcher(line);
            int from = 0;
            if (two.lookingAt()) {
                int n = Integer.parseInt(two.group(1));
                key.putIfAbsent(n, normalize(two.group(2)));
                secondary.putIfAbsent(n, normalize(two.group(3)));
                from = two.end();
            }
            Matcher m = PAIR.matcher(line);
            m.region(from, line.length());
            while (m.find()) {
                key.putIfAbsent(Integer.parseInt(m.group(1)), normalize(m.group(2)));
            }
        }
        return new AnswerKey(key, secondary);
    }

    /** Todos os tokens da linha casam com o padrão? Retorna-os, ou null. */
    private static List<String> tokens(String line, Pattern p) {
        String[] parts = line.strip().split("[\\s|;,]+");
        List<String> out = new ArrayList<>();
        for (String s : parts) {
            if (s.isEmpty()) continue;
            if (!p.matcher(s).matches()) return null;
            out.add(s);
        }
        return out;
    }

    private static String normalize(String answer) {
        String a = answer.toUpperCase(Locale.ROOT);
        return a.length() == 1 && a.charAt(0) >= 'A' && a.charAt(0) <= 'E' ? a : ANNULLED;
    }

    /**
     * Aplica o gabarito aos rascunhos. Questões da opção de Espanhol usam a segunda coluna, se houver.
     * Retorna quantas questões receberam resposta.
     */
    public static int apply(AnswerKey key, List<QuestionDraft> drafts) {
        int applied = 0;
        for (QuestionDraft d : drafts) {
            if (d.getNumber() == null) continue;
            boolean spanish = d.getSubjectName() != null && d.getSubjectName().toLowerCase(Locale.ROOT).contains("espanhol");
            String answer = spanish && key.secondary().containsKey(d.getNumber())
                    ? key.secondary().get(d.getNumber()) : key.get(d.getNumber());
            if (answer == null) {
                d.getWarnings().add("Questão não encontrada no gabarito.");
                continue;
            }
            if (ANNULLED.equals(answer)) {
                d.setAnnulled(true);
                applied++;
            } else if (d.setAnswerLetter(answer)) {
                applied++;
            } else {
                d.getWarnings().add("Gabarito indica " + answer + ", mas essa alternativa não foi encontrada.");
            }
        }
        return applied;
    }
}
