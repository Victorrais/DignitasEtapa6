package br.com.dignitas.extraction;

/** Área ocupada por uma imagem na página, em pontos, com origem no canto superior esquerdo. */
public record ImageBox(int page, double x, double y, double width, double height, boolean raster) {

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public double area() {
        return width * height;
    }

    /** Une duas áreas. */
    public ImageBox union(ImageBox o) {
        double x0 = Math.min(x, o.x), y0 = Math.min(y, o.y);
        double x1 = Math.max(right(), o.right()), y1 = Math.max(bottom(), o.bottom());
        return new ImageBox(page, x0, y0, x1 - x0, y1 - y0, raster || o.raster);
    }

    public boolean overlaps(ImageBox o, double margin) {
        return page == o.page && x - margin < o.right() && o.x - margin < right()
                && y - margin < o.bottom() && o.y - margin < bottom();
    }
}
