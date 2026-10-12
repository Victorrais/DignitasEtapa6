package br.com.dignitas.extraction;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * OCR com Tesseract (via Tess4J). Converte as linhas reconhecidas para {@link TextLine}
 * em pontos, para que o resto do pipeline trate texto nativo e OCR da mesma forma.
 * Uma instância não deve ser usada por várias threads ao mesmo tempo.
 */
public class OcrEngine {
    public static final int DPI = 300;

    private final Tesseract tesseract;

    public OcrEngine(Path tessdataDir) {
        tesseract = new Tesseract();
        tesseract.setDatapath(tessdataDir.toAbsolutePath().toString());
        tesseract.setLanguage(TessdataManager.LANGUAGE);
        tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
        // PSM 3: segmentação automática, que já separa colunas em blocos.
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);
        tesseract.setVariable("user_defined_dpi", Integer.toString(DPI));
        tesseract.setVariable("preserve_interword_spaces", "1");
    }

    /** Reconhece uma página renderizada a {@link #DPI} dpi. */
    public List<TextLine> recognize(BufferedImage image, int page) {
        double scale = 72.0 / DPI;
        List<TextLine> lines = new ArrayList<>();
        for (Word w : tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)) {
            String text = w.getText() == null ? "" : w.getText().replaceAll("\\s+", " ").strip();
            if (text.isEmpty()) continue;
            Rectangle r = w.getBoundingBox();
            double h = r.height * scale;
            lines.add(new TextLine(text, r.x * scale, r.y * scale, r.width * scale, h,
                    h * 0.85, false, page, w.getConfidence()));
        }
        return lines;
    }
}
