package br.com.dignitas.extraction;

/**
 * Uma linha (ou trecho de linha) de texto com sua posição na página, em pontos,
 * com origem no canto superior esquerdo. Vem do PDFBox (texto nativo) ou do Tesseract (OCR).
 * Também representa figuras (imagens) na ordem de leitura: nesse caso {@code figure} não é nulo
 * e o texto é vazio.
 *
 * @param confidence 0–100; 100 para texto nativo do PDF
 */
public record TextLine(String text, double x, double y, double width, double height,
                       double fontSize, boolean bold, int page, double confidence, ImageBox figure) {

    public TextLine(String text, double x, double y, double width, double height,
                    double fontSize, boolean bold, int page, double confidence) {
        this(text, x, y, width, height, fontSize, bold, page, confidence, null);
    }

    public static TextLine figure(ImageBox box) {
        return new TextLine("", box.x(), box.y(), box.width(), box.height(), 0, false, box.page(), 100, box);
    }

    public boolean isFigure() {
        return figure != null;
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public TextLine withText(String newText) {
        return new TextLine(newText, x, y, width, height, fontSize, bold, page, confidence, figure);
    }
}
