package br.com.dignitas.extraction;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Coloca as linhas em ordem de leitura: remove cabeçalhos/rodapés repetidos,
 * detecta páginas em duas colunas e lê a coluna esquerda antes da direita.
 */
public class LayoutAnalyzer {
    private static final double MARGIN_BAND = 0.08;
    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "(?i)^(p[áa]g(ina)?\\.?\\s*)?\\d{1,4}(\\s*(de|/)\\s*\\d{1,4})?$");

    public List<PageContent> analyze(List<PageContent> pages) {
        Set<String> repeated = repeatedMarginLines(pages);
        List<PageContent> result = new ArrayList<>();
        for (PageContent p : pages) {
            List<TextLine> kept = new ArrayList<>();
            for (TextLine l : p.lines()) {
                // Fora da área visível (marcas de impressão, nome do arquivo de diagramação).
                if (l.y() >= p.height() - 1 || l.bottom() <= 1) continue;
                if (inMargin(l, p) && (repeated.contains(key(l.text())) || PAGE_NUMBER.matcher(l.text().strip()).matches())) {
                    continue;
                }
                kept.add(l);
            }
            result.add(p.withLines(readingOrder(kept, p.width())));
        }
        return result;
    }

    private static boolean inMargin(TextLine l, PageContent p) {
        return l.bottom() < p.height() * MARGIN_BAND || l.y() > p.height() * (1 - MARGIN_BAND);
    }

    /** Texto sem dígitos, para que "Página 3" e "Página 4" sejam a mesma chave. */
    private static String key(String s) {
        return s.replaceAll("\\d+", "#").replaceAll("\\s+", " ").strip().toLowerCase();
    }

    private static Set<String> repeatedMarginLines(List<PageContent> pages) {
        if (pages.size() < 3) return Set.of();
        Map<String, Integer> counts = new HashMap<>();
        for (PageContent p : pages) {
            Set<String> onPage = new HashSet<>();
            for (TextLine l : p.lines()) {
                if (inMargin(l, p)) onPage.add(key(l.text()));
            }
            onPage.forEach(k -> counts.merge(k, 1, Integer::sum));
        }
        Set<String> result = new HashSet<>();
        counts.forEach((k, c) -> {
            // Repetida em 3 ou mais páginas na faixa de margem: cabeçalho/rodapé (inclui rodapés por área).
            if (c >= 3 && !k.isEmpty()) result.add(k);
        });
        return result;
    }

    /** Posição x da calha entre colunas, ou vazio se a página tem uma coluna só. */
    static OptionalDouble findGutter(List<TextLine> lines, double pageWidth) {
        if (lines.size() < 6 || pageWidth <= 0) return OptionalDouble.empty();
        int w = (int) Math.ceil(pageWidth);
        int[] coverage = new int[w + 1];
        for (TextLine l : lines) {
            int a = clamp((int) Math.floor(l.x()), w);
            int b = clamp((int) Math.ceil(l.right()), w);
            for (int i = a; i <= b; i++) coverage[i]++;
        }
        int threshold = Math.max(1, lines.size() / 30);
        int from = (int) (pageWidth * 0.3), to = (int) (pageWidth * 0.7);
        int bestStart = -1, bestLen = 0, runStart = -1;
        for (int i = from; i <= to + 1; i++) {
            boolean empty = i <= to && coverage[i] <= threshold;
            if (empty && runStart < 0) runStart = i;
            if (!empty && runStart >= 0) {
                if (i - runStart > bestLen) {
                    bestLen = i - runStart;
                    bestStart = runStart;
                }
                runStart = -1;
            }
        }
        if (bestLen < 6) return OptionalDouble.empty();
        double gutter = bestStart + bestLen / 2.0;
        long left = lines.stream().filter(l -> l.right() <= gutter).count();
        long right = lines.stream().filter(l -> l.x() >= gutter).count();
        if (left < lines.size() * 0.2 || right < lines.size() * 0.2) return OptionalDouble.empty();
        return OptionalDouble.of(gutter);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static final java.util.regex.Pattern BARE_LETTER = java.util.regex.Pattern.compile("^[A-E](\\s.*)?$");

    /**
     * Em páginas de coluna única, trechos com duas colunas lado a lado (quadros, comparações) são lidos
     * coluna por coluna. Um trecho precisa de pelo menos 3 linhas com duas partes separadas por um
     * espaço vertical comum. Grades de alternativas (A ao lado de B) são preservadas.
     */
    static List<TextLine> localColumns(List<TextLine> byY) {
        List<List<TextLine>> rows = new ArrayList<>();
        for (TextLine l : byY) {
            if (!rows.isEmpty() && Math.abs(rows.getLast().getFirst().y() - l.y()) <= 3) rows.getLast().add(l);
            else rows.add(new ArrayList<>(List.of(l)));
        }
        List<TextLine> out = new ArrayList<>();
        int i = 0;
        while (i < rows.size()) {
            double[] split = split(rows.get(i));
            if (split == null) {
                out.addAll(sortX(rows.get(i)));
                i++;
                continue;
            }
            double lo = split[0], hi = split[1];
            int j = i + 1, twoParts = 1;
            for (; j < rows.size(); j++) {
                List<TextLine> r = rows.get(j);
                double finalLo = lo, finalHi = hi;
                // Folga de 3 pt: a margem de texto justificado varia alguns centésimos de ponto.
                if (r.stream().anyMatch(l -> l.x() < finalHi - 3 && l.right() > finalLo + 3)) break;
                double[] s = split(r);
                if (s != null) {
                    double nlo = Math.max(lo, s[0]), nhi = Math.min(hi, s[1]);
                    if (nhi - nlo >= 4) {
                        lo = nlo;
                        hi = nhi;
                    } else if (s[1] < lo + 3 || s[0] > hi - 3) {
                        break; // a divisão desta linha não coincide com a do bloco
                    }
                    twoParts++;
                }
            }
            List<TextLine> block = new ArrayList<>();
            for (int k = i; k < j; k++) block.addAll(rows.get(k));
            boolean letters = block.stream().anyMatch(l -> BARE_LETTER.matcher(l.text().strip()).matches());
            if (twoParts >= 3 && !letters) {
                double mid = (lo + hi) / 2;
                block.stream().filter(l -> l.right() <= mid).forEach(out::add);
                block.stream().filter(l -> l.right() > mid).forEach(out::add);
            } else {
                for (int k = i; k < j; k++) out.addAll(sortX(rows.get(k)));
            }
            i = j;
        }
        return out;
    }

    /** Maior espaço horizontal (≥ 10 pt) entre as partes de uma linha: {fim da esquerda, início da direita}. */
    private static double[] split(List<TextLine> row) {
        if (row.size() < 2) return null;
        List<TextLine> s = sortX(row);
        double best = 0;
        double[] result = null;
        for (int k = 0; k + 1 < s.size(); k++) {
            double gap = s.get(k + 1).x() - s.get(k).right();
            if (gap >= 10 && gap > best) {
                best = gap;
                result = new double[]{s.get(k).right(), s.get(k + 1).x()};
            }
        }
        return result;
    }

    private static List<TextLine> sortX(List<TextLine> row) {
        List<TextLine> s = new ArrayList<>(row);
        s.sort(Comparator.comparingDouble(TextLine::x));
        return s;
    }

    static List<TextLine> readingOrder(List<TextLine> lines, double pageWidth) {
        List<TextLine> byY = new ArrayList<>(lines);
        byY.sort(Comparator.comparingDouble(TextLine::y).thenComparingDouble(TextLine::x));
        OptionalDouble gutter = findGutter(lines, pageWidth);
        if (gutter.isEmpty()) return localColumns(byY);

        double g = gutter.getAsDouble();
        List<TextLine> out = new ArrayList<>();
        List<TextLine> left = new ArrayList<>();
        List<TextLine> right = new ArrayList<>();
        for (TextLine l : byY) {
            if (l.right() <= g + 2) {
                left.add(l);
            } else if (l.x() >= g - 2) {
                right.add(l);
            } else {
                // Linha que atravessa a calha (título, instrução): fecha o bloco de colunas atual.
                out.addAll(left);
                out.addAll(right);
                left.clear();
                right.clear();
                out.add(l);
            }
        }
        out.addAll(left);
        out.addAll(right);
        return out;
    }
}
