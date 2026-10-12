package br.com.dignitas.domain;

import jakarta.persistence.*;

/** Texto de apoio, que pode ser compartilhado por várias questões ("Texto I – questões 1 a 5"). */
@Entity
@Table(name = "base_text")
public class BaseText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** Referência bibliográfica: "Disponível em: ...", "(Adaptado)". */
    @Column(length = 1000)
    private String source;

    /** Texto-base em forma de imagem (charge, cartaz, gráfico) ou imagem que acompanha o texto. */
    @Lob
    @Column(name = "image", columnDefinition = "LONGBLOB")
    private byte[] image;

    public BaseText() {
    }

    public BaseText(String content, String source) {
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
}
