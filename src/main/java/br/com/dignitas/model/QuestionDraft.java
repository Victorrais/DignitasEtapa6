package br.com.dignitas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Questão em edição: resultado da extração de um PDF ou formulário do editor.
 * Só vira entidade ao ser salva por um serviço.
 */
public class QuestionDraft {

    public static class AlternativeDraft {
        private String letter;
        private String text;
        private boolean correct;
        private byte[] image;
        /** Figura do PDF associada à alternativa (antes de virar imagem). */
        private Region figure;

        public AlternativeDraft(String letter, String text, boolean correct) {
            this.letter = letter;
            this.text = text;
            this.correct = correct;
        }

        public byte[] getImage() {
            return image;
        }

        public void setImage(byte[] image) {
            this.image = image;
        }

        public Region getFigure() {
            return figure;
        }

        public void setFigure(Region figure) {
            this.figure = figure;
        }

        /** Tem conteúdo (texto ou imagem)? */
        public boolean hasContent() {
            return !Texts.isBlank(text) || image != null || figure != null;
        }

        public String getLetter() {
            return letter;
        }

        public void setLetter(String letter) {
            this.letter = letter;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isCorrect() {
            return correct;
        }

        public void setCorrect(boolean correct) {
            this.correct = correct;
        }
    }

    /** Região da questão na página do PDF, em pontos (1/72 pol.), origem no canto superior esquerdo. */
    public record Region(int page, double x0, double y0, double x1, double y1) {
    }

    private Long id;
    private ExamInfo exam;
    private Integer number;
    private String subjectName;
    private BaseTextDraft baseText;
    private String statement = "";
    private final List<AlternativeDraft> alternatives = new ArrayList<>();
    private boolean annulled;
    private boolean trueFalse;
    private byte[] image;

    // Metadados da extração
    private Region region;
    /** Figuras do PDF que pertencem ao enunciado (viram a imagem da questão). */
    private final List<Region> figures = new ArrayList<>();
    private double confidence = 1.0;
    private boolean ocr;
    private final List<String> warnings = new ArrayList<>();
    private boolean duplicate;
    private boolean included = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ExamInfo getExam() {
        return exam;
    }

    public void setExam(ExamInfo exam) {
        this.exam = exam;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public BaseTextDraft getBaseText() {
        return baseText;
    }

    public void setBaseText(BaseTextDraft baseText) {
        this.baseText = baseText;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement == null ? "" : statement;
    }

    public List<AlternativeDraft> getAlternatives() {
        return alternatives;
    }

    public AlternativeDraft addAlternative(String letter, String text, boolean correct) {
        AlternativeDraft a = new AlternativeDraft(letter, text, correct);
        alternatives.add(a);
        return a;
    }

    public Optional<AlternativeDraft> correctAlternative() {
        return alternatives.stream().filter(AlternativeDraft::isCorrect).findFirst();
    }

    /** Marca como correta a alternativa da letra informada (e desmarca as demais). */
    public boolean setAnswerLetter(String letter) {
        boolean found = false;
        for (AlternativeDraft a : alternatives) {
            boolean match = a.getLetter().equalsIgnoreCase(letter);
            a.setCorrect(match);
            found |= match;
        }
        return found;
    }

    public boolean isAnnulled() {
        return annulled;
    }

    public void setAnnulled(boolean annulled) {
        this.annulled = annulled;
    }

    public boolean isTrueFalse() {
        return trueFalse;
    }

    public void setTrueFalse(boolean trueFalse) {
        this.trueFalse = trueFalse;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public List<Region> getFigures() {
        return figures;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = Math.max(0, Math.min(1, confidence));
    }

    public boolean isOcr() {
        return ocr;
    }

    public void setOcr(boolean ocr) {
        this.ocr = ocr;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public boolean isIncluded() {
        return included;
    }

    public void setIncluded(boolean included) {
        this.included = included;
    }

    /** Hash de conteúdo usado para detectar duplicidade (mesma regra do banco). */
    public String contentHash() {
        StringBuilder sb = new StringBuilder(Texts.normalize(statement));
        if (image != null) sb.append("#img").append(Texts.sha256(image));
        for (AlternativeDraft a : alternatives) {
            sb.append('|').append(Texts.normalize(a.getText()));
            if (a.getImage() != null) sb.append("#img").append(Texts.sha256(a.getImage()));
        }
        return Texts.sha256(sb.toString());
    }

    /** Lista os problemas que impedem salvar a questão; vazia se estiver válida. */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        boolean hasBase = baseText != null && (!baseText.isEmpty() || baseText.getImage() != null);
        if (Texts.isBlank(statement) && image == null && !hasBase) errors.add("O enunciado está vazio.");
        if (alternatives.size() < 2) errors.add("A questão precisa de pelo menos 2 alternativas.");
        for (AlternativeDraft a : alternatives) {
            if (!a.hasContent()) {
                errors.add("A alternativa " + a.getLetter() + " está vazia.");
            }
        }
        long correct = alternatives.stream().filter(AlternativeDraft::isCorrect).count();
        if (!annulled && correct != 1) errors.add("Marque exatamente uma alternativa correta (ou marque a questão como anulada).");
        return errors;
    }
}
