package br.com.dignitas.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "alternative")
public class Alternative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Question question;

    /** A–E; nas questões de Certo/Errado usa-se "C" e "E". */
    @Column(nullable = false, length = 1)
    private String letter;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(name = "is_answer", nullable = false)
    private boolean answer;

    /** Alternativas em forma de imagem (gráficos, mapas, figuras). */
    @Lob
    @Column(name = "image", columnDefinition = "LONGBLOB")
    private byte[] image;

    public Alternative() {
    }

    public Alternative(String letter, String statement, boolean answer) {
        this.letter = letter;
        this.statement = statement;
        this.answer = answer;
    }

    public Long getId() {
        return id;
    }

    public Question getQuestion() {
        return question;
    }

    void setQuestion(Question question) {
        this.question = question;
    }

    public String getLetter() {
        return letter;
    }

    public void setLetter(String letter) {
        this.letter = letter;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public boolean isAnswer() {
        return answer;
    }

    public void setAnswer(boolean answer) {
        this.answer = answer;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }
}
