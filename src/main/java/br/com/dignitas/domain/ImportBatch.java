package br.com.dignitas.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/** Uma importação de PDF. Excluí-la remove (em cascata no banco) todas as questões geradas por ela. */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Exam exam;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** SHA-256 do arquivo: impede importar o mesmo PDF duas vezes. */
    @Column(name = "file_hash", nullable = false, unique = true, length = 64)
    private String fileHash;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "ocr_pages", nullable = false)
    private int ocrPages;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @PrePersist
    void prePersist() {
        if (importedAt == null) importedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public int getOcrPages() {
        return ocrPages;
    }

    public void setOcrPages(int ocrPages) {
        this.ocrPages = ocrPages;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }
}
