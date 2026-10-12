package br.com.dignitas.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extrai o texto nativo de uma página com a posição de cada linha.
 * <p>
 * Em vez de usar o texto corrido do {@link PDFTextStripper} (que mistura colunas lado a lado),
 * coletamos os glifos e montamos as linhas, quebrando-as onde há um espaço horizontal grande
 * (calha entre colunas). A ordem de leitura é decidida depois, pelo {@link LayoutAnalyzer}.
 */
public class PdfTextExtractor {

    private final PdfImageLocator images = new PdfImageLocator();

    public PageContent extract(PDDocument doc, int pageIndex) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        PDRectangle box = page.getCropBox();
        boolean rotated = page.getRotation() % 180 != 0;
        double width = rotated ? box.getHeight() : box.getWidth();
        double height = rotated ? box.getWidth() : box.getHeight();

        GlyphCollector collector = new GlyphCollector();
        collector.setStartPage(pageIndex + 1);
        collector.setEndPage(pageIndex + 1);
        collector.getText(doc);
        List<TextLine> lines = buildLines(collector.glyphs, pageIndex, width);
        for (ImageBox img : images.locate(doc, pageIndex)) {
            // Só imagens rasterizadas: retângulos vetoriais costumam ser molduras e faixas de título.
            if (img.raster()) lines.add(TextLine.figure(img));
        }
        return new PageContent(pageIndex, width, height, lines, false);
    }

    /** Coleta os glifos horizontais, descartando duplicatas usadas para simular negrito. */
    private static final class GlyphCollector extends PDFTextStripper {
        final List<TextPosition> glyphs = new ArrayList<>();

        GlyphCollector() throws IOException {
            setSortByPosition(false);
        }

        @Override
        protected void processTextPosition(TextPosition t) {
            if (t.getDir() != 0f) return; // texto vertical (margens, carimbos)
            if (t.getFontSizeInPt() > 0 && t.getFontSizeInPt() < 3) return; // microtexto de segurança
            String u = t.getUnicode();
            if (u == null || u.isEmpty()) return;
            int from = Math.max(0, glyphs.size() - 40);
            for (int i = glyphs.size() - 1; i >= from; i--) {
                TextPosition o = glyphs.get(i);
                if (u.equals(o.getUnicode())
                        && Math.abs(o.getXDirAdj() - t.getXDirAdj()) < Math.max(1f, t.getWidthDirAdj() * 0.3f)
                        && Math.abs(o.getYDirAdj() - t.getYDirAdj()) < 1f) {
                    return;
                }
            }
            glyphs.add(t);
        }
    }

    static List<TextLine> buildLines(List<TextPosition> glyphs, int page, double pageWidth) {
        List<List<TextPosition>> rows = rows(glyphs);
        double[] gutter = findGutter(rows, pageWidth);
        List<TextLine> lines = new ArrayList<>();
        for (List<TextPosition> r : rows) splitRow(r, page, gutter, lines);
        return lines;
    }

    /** Agrupa os glifos em linhas visuais (mesma altura). */
    static List<List<TextPosition>> rows(List<TextPosition> glyphs) {
        List<TextPosition> sorted = new ArrayList<>(glyphs);
        sorted.sort(Comparator.comparingDouble(TextPosition::getYDirAdj).thenComparingDouble(TextPosition::getXDirAdj));

        List<List<TextPosition>> rows = new ArrayList<>();
        List<TextPosition> row = new ArrayList<>();
        double rowY = Double.NaN;
        for (TextPosition g : sorted) {
            double tolerance = Math.max(1.5, size(g) * 0.35);
            if (!row.isEmpty() && g.getYDirAdj() - rowY > tolerance) {
                rows.add(row);
                row = new ArrayList<>();
            }
            if (row.isEmpty()) rowY = g.getYDirAdj();
            row.add(g);
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    /**
     * Procura, no terço central da página, uma faixa vertical sem glifos em quase nenhuma linha:
     * a calha entre duas colunas. Retorna {início, fim} em pontos, ou {@code null}.
     */
    static double[] findGutter(List<List<TextPosition>> rows, double pageWidth) {
        if (rows.size() < 8 || pageWidth <= 0) return null;
        int w = (int) Math.ceil(pageWidth) + 1;
        int[] count = new int[w + 1];
        for (List<TextPosition> r : rows) {
            boolean[] covered = new boolean[w + 1];
            for (TextPosition g : r) {
                if (g.getUnicode().isBlank()) continue;
                int a = clamp((int) Math.floor(g.getXDirAdj()), w);
                int b = clamp((int) Math.ceil(g.getXDirAdj() + g.getWidthDirAdj()), w);
                for (int i = a; i <= b; i++) covered[i] = true;
            }
            for (int i = 0; i <= w; i++) if (covered[i]) count[i]++;
        }
        int threshold = Math.max(1, (int) (rows.size() * 0.03));
        int from = (int) (pageWidth * 0.3), to = (int) (pageWidth * 0.7);
        int bestStart = -1, bestLen = 0, runStart = -1;
        for (int i = from; i <= to + 1; i++) {
            boolean empty = i <= to && count[i] <= threshold;
            if (empty && runStart < 0) runStart = i;
            if (!empty && runStart >= 0) {
                if (i - runStart > bestLen) {
                    bestLen = i - runStart;
                    bestStart = runStart;
                }
                runStart = -1;
            }
        }
        if (bestLen < 4) return null;
        double g0 = bestStart, g1 = bestStart + bestLen;
        int left = 0, right = 0;
        for (List<TextPosition> r : rows) {
            boolean l = false, rr = false;
            for (TextPosition g : r) {
                if (g.getXDirAdj() + g.getWidthDirAdj() <= g0 + 0.5) l = true;
                if (g.getXDirAdj() >= g1 - 0.5) rr = true;
            }
            if (l) left++;
            if (rr) right++;
        }
        if (left < rows.size() * 0.25 || right < rows.size() * 0.25) return null;
        return new double[]{g0, g1};
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static double size(TextPosition g) {
        double s = g.getFontSizeInPt();
        if (s <= 1) s = g.getHeightDir() * 1.4;
        return s <= 1 ? 10 : s;
    }

    /** Quebra uma linha visual em trechos separados por espaços grandes (ou pela calha) e monta o texto. */
    private static void splitRow(List<TextPosition> row, int page, double[] gutter, List<TextLine> out) {
        row.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
        StringBuilder text = new StringBuilder();
        List<TextPosition> segment = new ArrayList<>();
        TextPosition prev = null;
        for (TextPosition g : row) {
            String u = g.getUnicode();
            boolean blank = u.isBlank();
            // Espaços não contam: um espaço final pode invadir a calha entre colunas.
            if (prev != null && !blank) {
                double prevRight = prev.getXDirAdj() + prev.getWidthDirAdj();
                double gap = g.getXDirAdj() - prevRight;
                double fs = size(g);
                boolean crossesGutter = gutter != null && prevRight <= gutter[0] + 1 && g.getXDirAdj() >= gutter[1] - 1;
                if (gap > Math.max(1.8 * fs, 14) || crossesGutter) {
                    emit(segment, text, page, out);
                    segment.clear();
                    text.setLength(0);
                } else if (gap > fs * 0.18 && !text.isEmpty() && text.charAt(text.length() - 1) != ' ') {
                    text.append(' ');
                }
            }
            if (blank) {
                if (!text.isEmpty() && text.charAt(text.length() - 1) != ' ') text.append(' ');
            } else {
                text.append(u);
                segment.add(g);
                prev = g;
            }
        }
        emit(segment, text, page, out);
    }

    private static void emit(List<TextPosition> segment, StringBuilder text, int page, List<TextLine> out) {
        String s = text.toString().strip();
        if (segment.isEmpty() || s.isEmpty()) return;
        double x0 = Double.MAX_VALUE, x1 = 0, top = Double.MAX_VALUE, bottom = 0, fs = 0;
        int bold = 0;
        for (TextPosition g : segment) {
            x0 = Math.min(x0, g.getXDirAdj());
            x1 = Math.max(x1, g.getXDirAdj() + g.getWidthDirAdj());
            double h = Math.max(g.getHeightDir(), size(g) * 0.7);
            top = Math.min(top, g.getYDirAdj() - h);
            bottom = Math.max(bottom, g.getYDirAdj());
            fs = Math.max(fs, size(g));
            if (isBold(g.getFont())) bold++;
        }
        out.add(new TextLine(s, x0, top, x1 - x0, bottom - top, fs, bold * 2 > segment.size(), page, 100));
    }

    private static boolean isBold(PDFont font) {
        if (font == null) return false;
        String name = font.getName();
        if (name != null && name.toLowerCase().contains("bold")) return true;
        PDFontDescriptor d = font.getFontDescriptor();
        return d != null && (d.isForceBold() || d.getFontWeight() >= 600);
    }
}
