package br.com.dignitas.service;

import br.com.dignitas.service.Dtos.ImportRow;

import java.time.format.DateTimeFormatter;

/** O mesmo arquivo PDF já foi importado (mesmo hash). */
public class AlreadyImportedException extends RuntimeException {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ImportRow existing;

    public AlreadyImportedException(ImportRow existing) {
        super("Este PDF já foi importado em " + FORMAT.format(existing.importedAt()) + " (" + existing.questions()
                + " questões). Para importá-lo de novo, exclua a importação anterior na tela Importações.");
        this.existing = existing;
    }

    public ImportRow existing() {
        return existing;
    }
}
