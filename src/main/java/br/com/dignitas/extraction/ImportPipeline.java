package br.com.dignitas.extraction;

import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.Texts;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * PDF → questões: extração (texto nativo ou OCR por página) → layout → segmentação → gabarito.
 * Nada é gravado aqui; o resultado vai para a tela de revisão.
 */
public class ImportPipeline {

    public interface Progress {
        void update(int done, int total, String message);
    }

    public record Result(File file, String fileHash, int pageCount, int ocrPages,
                         List<QuestionDraft> questions, List<String> warnings, int answersApplied) {
    }

    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final LayoutAnalyzer layout = new LayoutAnalyzer();

    /**
     * @param ocr motor de OCR, ou {@code null} se o pacote de idioma não estiver instalado
     */
    public Result run(File pdf, File answerKey, OcrMode mode, OcrEngine ocr,
                      Progress progress, BooleanSupplier cancelled) throws IOException {
        List<String> warnings = new ArrayList<>();
        String hash = Texts.sha256(Files.readAllBytes(pdf.toPath()));

        int[] ocrPages = {0};
        List<QuestionDraft> questions;
        int pageCount;
        int applied = 0;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            pageCount = doc.getNumberOfPages();
            List<PageContent> pages = readPages(doc, mode, ocr, progress, cancelled, warnings, ocrPages, "Lendo prova");
            if (cancelled.getAsBoolean()) throw new CancellationException();

            progress.update(pageCount, pageCount, "Identificando questões…");
            questions = new QuestionSegmenter().segment(layout.analyze(pages));
            if (questions.isEmpty()) {
                warnings.add("Nenhuma questão foi identificada. Confira se o PDF tem texto legível "
                        + "ou tente o modo \"Forçar OCR\".");
            }

            if (answerKey != null) {
                try (PDDocument keyDoc = Loader.loadPDF(answerKey)) {
                    List<PageContent> keyPages = readPages(keyDoc, mode, ocr, progress, cancelled, warnings, new int[1], "Lendo gabarito");
                    AnswerKeyParser.AnswerKey key = new AnswerKeyParser().parse(keyPages);
                    if (key.isEmpty()) warnings.add("Não foi possível ler respostas no arquivo de gabarito.");
                    applied = AnswerKeyParser.apply(key, questions);
                }
            } else {
                warnings.add("Sem gabarito: marque a alternativa correta de cada questão na revisão.");
            }

            progress.update(pageCount, pageCount, "Recortando figuras…");
            new FigureRenderer(doc).render(questions);
        }

        Set<String> seen = new HashSet<>();
        for (QuestionDraft q : questions) {
            if (!seen.add(q.contentHash())) {
                q.setDuplicate(true);
                q.setIncluded(false);
                q.getWarnings().add("Repetida neste mesmo arquivo.");
            }
        }
        return new Result(pdf, hash, pageCount, ocrPages[0], questions, warnings, applied);
    }

    private List<PageContent> readPages(PDDocument doc, OcrMode mode, OcrEngine ocr, Progress progress,
                                        BooleanSupplier cancelled, List<String> warnings, int[] ocrPages,
                                        String label) throws IOException {
        int total = doc.getNumberOfPages();
        PDFRenderer renderer = new PDFRenderer(doc);
        List<PageContent> pages = new ArrayList<>();
        List<Integer> scannedWithoutOcr = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (cancelled.getAsBoolean()) throw new CancellationException();
            PageContent page = extractor.extract(doc, i);
            boolean wantOcr = mode == OcrMode.ALWAYS || mode == OcrMode.AUTO && needsOcr(page);
            if (wantOcr && ocr != null) {
                progress.update(i, total, label + ": página " + (i + 1) + " de " + total + " (OCR)");
                BufferedImage img = renderer.renderImageWithDPI(i, OcrEngine.DPI, ImageType.GRAY);
                try {
                    page = new PageContent(i, page.width(), page.height(), ocr.recognize(img, i), true);
                    ocrPages[0]++;
                } catch (RuntimeException | LinkageError e) {
                    // Biblioteca nativa ausente ou modelo inválido: segue sem OCR.
                    warnings.add("OCR indisponível (" + e.getMessage() + "). As demais páginas foram lidas sem OCR.");
                    ocr = null;
                    scannedWithoutOcr.add(i + 1);
                }
            } else {
                progress.update(i, total, label + ": página " + (i + 1) + " de " + total);
                if (wantOcr) scannedWithoutOcr.add(i + 1);
            }
            pages.add(page);
        }
        if (!scannedWithoutOcr.isEmpty()) {
            warnings.add("Páginas sem texto legível " + scannedWithoutOcr
                    + ": instale o pacote de OCR em Configurações para lê-las.");
        }
        return pages;
    }

    /** Números (1-based) das páginas sem texto legível, que precisariam de OCR. */
    public List<Integer> pagesNeedingOcr(File pdf) throws IOException {
        List<Integer> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                if (needsOcr(extractor.extract(doc, i))) pages.add(i + 1);
            }
        }
        return pages;
    }

    /** Página digitalizada ou com fonte sem mapeamento de caracteres (texto "lixo"). */
    static boolean needsOcr(PageContent page) {
        int total = 0, good = 0;
        for (TextLine l : page.lines()) {
            for (char c : l.text().toCharArray()) {
                if (Character.isWhitespace(c)) continue;
                total++;
                if (Character.isLetterOrDigit(c) && c < 0x0250 || ".,;:!?()[]\"'“”‘’-–—/%ºª°§$".indexOf(c) >= 0) good++;
            }
        }
        return total < 40 || good < total * 0.75;
    }

    public static class CancellationException extends RuntimeException {
        public CancellationException() {
            super("Importação cancelada");
        }
    }
}
