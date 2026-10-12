package br.com.dignitas.extraction;

import java.util.List;

/** Linhas de uma página, já em ordem de leitura depois do {@link LayoutAnalyzer}. */
public record PageContent(int index, double width, double height, List<TextLine> lines, boolean ocr) {

    public PageContent withLines(List<TextLine> newLines) {
        return new PageContent(index, width, height, List.copyOf(newLines), ocr);
    }
}
