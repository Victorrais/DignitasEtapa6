package br.com.dignitas.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nulo para questões cadastradas manualmente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_text_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private BaseText baseText;

    /** Número da questão na prova original. */
    @Column(name = "number")
    private Integer number;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    /** SHA-256 do enunciado + alternativas normalizados; detecta duplicidade. */
    @Column(name = "content_hash", nullable = false, unique = true, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private boolean annulled;

    /** Marcada pelo usuário para revisar depois. */
    @Column(nullable = false)
    private boolean flagged;

    @Lob
    @Column(name = "image", columnDefinition = "LONGBLOB")
    private byte[] image;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("letter ASC")
    private List<Alternative> alternatives = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addAlternative(Alternative alternative) {
        alternative.setQuestion(this);
        alternatives.add(alternative);
    }

    public Optional<Alternative> correctAlternative() {
        return alternatives.stream().filter(Alternative::isAnswer).findFirst();
    }

    public Long getId() {
        return id;
    }

    public ImportBatch getImportBatch() {
        return importBatch;
    }

    public void setImportBatch(ImportBatch importBatch) {
        this.importBatch = importBatch;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public BaseText getBaseText() {
        return baseText;
    }

    public void setBaseText(BaseText baseText) {
        this.baseText = baseText;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public boolean isAnnulled() {
        return annulled;
    }

    public void setAnnulled(boolean annulled) {
        this.annulled = annulled;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Alternative> getAlternatives() {
        return alternatives;
    }
}
