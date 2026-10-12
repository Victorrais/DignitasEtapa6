package br.com.dignitas.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** Uma resposta do usuário. A mesma questão pode ser respondida várias vezes. */
@Entity
@Table(name = "attempt")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Question question;

    /** Fica nulo se a alternativa for removida ao editar a questão. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternative_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Alternative alternative;

    /** Letra escolhida, preservada mesmo se a alternativa for editada/removida. */
    @Column(name = "chosen_letter", length = 1)
    private String chosenLetter;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    public Attempt() {
    }

    public Attempt(Question question, Alternative alternative, boolean correct) {
        this.question = question;
        this.alternative = alternative;
        this.chosenLetter = alternative == null ? null : alternative.getLetter();
        this.correct = correct;
    }

    @PrePersist
    void prePersist() {
        if (answeredAt == null) answeredAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Question getQuestion() {
        return question;
    }

    public Alternative getAlternative() {
        return alternative;
    }

    public String getChosenLetter() {
        return chosenLetter;
    }

    public boolean isCorrect() {
        return correct;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }
}
