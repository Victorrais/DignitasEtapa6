package br.com.dignitas.extraction;

public enum OcrMode {
    AUTO("Automático (texto do PDF; OCR nas páginas digitalizadas)"),
    TEXT_ONLY("Somente texto do PDF (sem OCR)"),
    ALWAYS("Forçar OCR em todas as páginas");

    private final String label;

    OcrMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
