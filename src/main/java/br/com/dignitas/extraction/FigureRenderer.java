package br.com.dignitas.extraction;

import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.QuestionDraft.AlternativeDraft;
import br.com.dignitas.model.QuestionDraft.Region;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

/**
 * Converte as figuras localizadas no PDF (regiões) em imagens PNG para questões,
 * alternativas e textos-base. Várias figuras do mesmo elemento são empilhadas numa imagem só.
 */
public class FigureRenderer {
    static final float DPI = 150;

    private final PDFRenderer renderer;
    private final Map<Integer, BufferedImage> pages = new HashMap<>();

    public FigureRenderer(PDDocument doc) {
        this.renderer = new PDFRenderer(doc);
    }

    public void render(List<QuestionDraft> questions) throws IOException {
        Set<BaseTextDraft> done = Collections.newSetFromMap(new IdentityHashMap<>());
        for (QuestionDraft q : questions) {
            if (!q.getFigures().isEmpty() && q.getImage() == null) q.setImage(png(q.getFigures()));
            for (AlternativeDraft a : q.getAlternatives()) {
                if (a.getFigure() != null && a.getImage() == null) a.setImage(png(List.of(a.getFigure())));
            }
            BaseTextDraft b = q.getBaseText();
            if (b != null && done.add(b) && !b.getFigures().isEmpty() && b.getImage() == null) {
                b.setImage(png(b.getFigures()));
            }
        }
        pages.clear();
    }

    private byte[] png(List<Region> regions) throws IOException {
        List<BufferedImage> crops = new ArrayList<>();
        for (Region r : regions) crops.add(crop(r));
        if (crops.size() == 1) return PdfPageRenderer.png(crops.getFirst());
        int w = crops.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int gap = 12;
        int h = crops.stream().mapToInt(BufferedImage::getHeight).sum() + gap * (crops.size() - 1);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        int y = 0;
        for (BufferedImage c : crops) {
            g.drawImage(c, 0, y, null);
            y += c.getHeight() + gap;
        }
        g.dispose();
        return PdfPageRenderer.png(out);
    }

    private BufferedImage crop(Region r) throws IOException {
        BufferedImage page = pages.get(r.page());
        if (page == null) {
            page = renderer.renderImageWithDPI(r.page(), DPI, ImageType.RGB);
            pages.put(r.page(), page);
        }
        double s = DPI / 72.0;
        int pad = 2;
        int x = Math.max(0, (int) Math.floor(r.x0() * s) - pad);
        int y = Math.max(0, (int) Math.floor(r.y0() * s) - pad);
        int x1 = Math.min(page.getWidth(), (int) Math.ceil(r.x1() * s) + pad);
        int y1 = Math.min(page.getHeight(), (int) Math.ceil(r.y1() * s) + pad);
        if (x1 <= x || y1 <= y) return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        return page.getSubimage(x, y, x1 - x, y1 - y);
    }
}
