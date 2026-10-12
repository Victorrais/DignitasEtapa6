package br.com.dignitas.model;

/**
 * Texto-base em edição. A mesma instância é compartilhada pelas questões que o usam,
 * de modo que editá-lo em uma questão reflete nas demais.
 */
public class BaseTextDraft {
    /** Id do {@code BaseText} já salvo, ou nulo se novo. */
    private Long id;
    private String content;
    private String source;
    private byte[] image;
    /** Figuras do PDF que fazem parte do texto-base (antes de virar imagem). */
    private final java.util.List<QuestionDraft.Region> figures = new java.util.ArrayList<>();

    public BaseTextDraft() {
    }

    public BaseTextDraft(Long id, String content, String source) {
        this.id = id;
        this.content = content;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public java.util.List<QuestionDraft.Region> getFigures() {
        return figures;
    }

    /** Sem texto e sem imagem. */
    public boolean isEmpty() {
        return Texts.isBlank(content) && image == null && figures.isEmpty();
    }
}
