package br.com.dignitas.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Localiza figuras numa página: imagens rasterizadas e desenhos vetoriais (gráficos, tabelas, esquemas).
 * Fios finos e retângulos de moldura são ignorados.
 */
public class PdfImageLocator {
    /** Menor lado aceito para uma figura, em pontos. */
    private static final double MIN_SIDE = 14;

    public List<ImageBox> locate(PDDocument doc, int pageIndex) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        Engine engine = new Engine(page, pageIndex);
        engine.processPage(page);
        engine.flushPath();
        return merge(engine.boxes, pageIndex);
    }

    private static final class Engine extends PDFGraphicsStreamEngine {
        final List<ImageBox> boxes = new ArrayList<>();
        final int pageIndex;
        final double pageHeight;
        final double pageWidth;
        final double offsetX;
        double px0 = Double.MAX_VALUE, py0 = Double.MAX_VALUE, px1 = -Double.MAX_VALUE, py1 = -Double.MAX_VALUE;
        Point2D current = new Point2D.Double();

        Engine(PDPage page, int pageIndex) {
            super(page);
            this.pageIndex = pageIndex;
            PDRectangle box = page.getCropBox();
            pageHeight = box.getHeight() + box.getLowerLeftY();
            pageWidth = box.getWidth();
            offsetX = box.getLowerLeftX();
        }

        @Override
        public void drawImage(PDImage pdImage) {
            Matrix m = getGraphicsState().getCurrentTransformationMatrix();
            double w = Math.abs(m.getScalingFactorX());
            double h = Math.abs(m.getScalingFactorY());
            double x = m.getTranslateX() - offsetX;
            double y = pageHeight - m.getTranslateY() - h;
            add(x, y, w, h, true);
        }

        private void add(double x, double y, double w, double h, boolean raster) {
            if (w < MIN_SIDE || h < MIN_SIDE) return;
            // Ignora fundos e molduras de página inteira e elementos fora da página.
            if (w > pageWidth * 0.9 && h > pageHeight * 0.8) return;
            if (x + w < 0 || y + h < 0 || x > pageWidth || y > pageHeight) return;
            boxes.add(new ImageBox(pageIndex, x, y, w, h, raster));
        }

        private void extend(Point2D p) {
            px0 = Math.min(px0, p.getX());
            px1 = Math.max(px1, p.getX());
            py0 = Math.min(py0, p.getY());
            py1 = Math.max(py1, p.getY());
        }

        void flushPath() {
            if (px0 != Double.MAX_VALUE) {
                add(px0 - offsetX, pageHeight - py1, px1 - px0, py1 - py0, false);
            }
            px0 = py0 = Double.MAX_VALUE;
            px1 = py1 = -Double.MAX_VALUE;
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            extend(p0);
            extend(p1);
            extend(p2);
            extend(p3);
        }

        @Override
        public void moveTo(float x, float y) {
            current = new Point2D.Double(x, y);
            extend(current);
        }

        @Override
        public void lineTo(float x, float y) {
            current = new Point2D.Double(x, y);
            extend(current);
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            extend(new Point2D.Double(x1, y1));
            extend(new Point2D.Double(x2, y2));
            current = new Point2D.Double(x3, y3);
            extend(current);
        }

        @Override
        public Point2D getCurrentPoint() {
            return current;
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
            resetPath();
        }

        @Override
        public void strokePath() {
            flushVector();
        }

        @Override
        public void fillPath(int windingRule) {
            flushVector();
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            flushVector();
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) {
        }

        /** Cada caminho desenhado vira uma caixa candidata; caixas próximas são unidas depois. */
        private void flushVector() {
            flushPath();
        }

        private void resetPath() {
            px0 = py0 = Double.MAX_VALUE;
            px1 = py1 = -Double.MAX_VALUE;
        }
    }

    /** Une caixas que se sobrepõem ou se tocam (partes de um mesmo gráfico). */
    static List<ImageBox> merge(List<ImageBox> boxes, int page) {
        List<ImageBox> result = new ArrayList<>(boxes);
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < result.size(); i++) {
                for (int j = i + 1; j < result.size(); j++) {
                    if (result.get(i).overlaps(result.get(j), 3)) {
                        ImageBox u = result.get(i).union(result.get(j));
                        result.remove(j);
                        result.set(i, u);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        result.removeIf(b -> b.width() < MIN_SIDE || b.height() < MIN_SIDE);
        return result;
    }
}
