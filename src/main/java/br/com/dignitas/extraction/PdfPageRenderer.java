package br.com.dignitas.extraction;

import br.com.dignitas.model.QuestionDraft.Region;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/** Renderiza páginas de um PDF aberto (pré-visualização na revisão e recortes de figuras). */
public class PdfPageRenderer implements AutoCloseable {
    private final PDDocument doc;
    private final PDFRenderer renderer;

    public PdfPageRenderer(File file) throws IOException {
        doc = Loader.loadPDF(file);
        renderer = new PDFRenderer(doc);
    }

    public int pageCount() {
        return doc.getNumberOfPages();
    }

    /** Largura da página em pontos, considerando a rotação. */
    public float pageWidth(int page) {
        var p = doc.getPage(page);
        var box = p.getCropBox();
        return p.getRotation() % 180 != 0 ? box.getHeight() : box.getWidth();
    }

    public synchronized BufferedImage render(int page, float dpi) throws IOException {
        return renderer.renderImageWithDPI(page, dpi, ImageType.RGB);
    }

    /** Recorta a região (em pontos) com uma pequena margem e devolve um PNG. */
    public byte[] cropPng(Region region, float dpi) throws IOException {
        BufferedImage page = render(region.page(), dpi);
        double s = dpi / 72.0;
        int pad = (int) (6 * s);
        int x = Math.max(0, (int) (region.x0() * s) - pad);
        int y = Math.max(0, (int) (region.y0() * s) - pad);
        int w = Math.min(page.getWidth() - x, (int) ((region.x1() - region.x0()) * s) + 2 * pad);
        int h = Math.min(page.getHeight() - y, (int) ((region.y1() - region.y0()) * s) + 2 * pad);
        if (w <= 0 || h <= 0) throw new IOException("Região inválida");
        return png(page.getSubimage(x, y, w, h));
    }

    public static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        doc.close();
    }
}
