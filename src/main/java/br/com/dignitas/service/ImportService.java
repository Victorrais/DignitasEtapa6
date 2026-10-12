package br.com.dignitas.service;

import br.com.dignitas.domain.BaseText;
import br.com.dignitas.domain.Exam;
import br.com.dignitas.domain.ImportBatch;
import br.com.dignitas.domain.Question;
import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.service.Dtos.ImportRow;
import br.com.dignitas.service.Dtos.SaveSummary;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.*;

public class ImportService {

    /** Retorna a importação anterior do mesmo arquivo, se houver. */
    public Optional<ImportRow> findByFileHash(String fileHash) {
        return query(" where i.fileHash = :h", fileHash).stream().findFirst();
    }

    /**
     * Grava as questões revisadas. Rascunhos não incluídos, inválidos ou duplicados são ignorados.
     *
     * @throws AlreadyImportedException se o mesmo arquivo já tiver sido importado (nada é gravado)
     */
    public SaveSummary save(ExamInfo exam, String fileName, String fileHash, int pageCount, int ocrPages,
                            List<QuestionDraft> drafts) {
        return Jpa.tx(em -> {
            // Confere de novo aqui: a tela de importação só verifica no passo 1.
            List<ImportRow> existing = query(em, " where i.fileHash = :h", fileHash);
            if (!existing.isEmpty()) throw new AlreadyImportedException(existing.getFirst());

            Exam e = exam == null || exam.isEmpty() ? null : CatalogService.findOrCreateExam(em, exam);
            ImportBatch batch = new ImportBatch();
            batch.setExam(e);
            batch.setFileName(fileName);
            batch.setFileHash(fileHash);
            batch.setPageCount(pageCount);
            batch.setOcrPages(ocrPages);
            em.persist(batch);

            Set<String> seen = new HashSet<>();
            List<String> hashes = drafts.stream().filter(QuestionDraft::isIncluded).map(QuestionDraft::contentHash).toList();
            if (!hashes.isEmpty()) {
                seen.addAll(em.createQuery("select q.contentHash from Question q where q.contentHash in :h", String.class)
                        .setParameter("h", hashes).getResultList());
            }

            Map<BaseTextDraft, BaseText> baseTexts = new IdentityHashMap<>();
            int saved = 0;
            int skipped = 0;
            for (QuestionDraft d : drafts) {
                if (!d.isIncluded() || !d.validate().isEmpty()) continue;
                String hash = d.contentHash();
                if (!seen.add(hash)) {
                    skipped++;
                    continue;
                }
                Question q = new Question();
                q.setImportBatch(batch);
                q.setExam(e);
                QuestionMapper.apply(em, q, d, hash, baseTexts);
                em.persist(q);
                saved++;
            }
            return new SaveSummary(saved, skipped);
        });
    }

    public List<ImportRow> list() {
        return query("", null);
    }

    private List<ImportRow> query(String where, String fileHash) {
        return Jpa.read(em -> query(em, where, fileHash));
    }

    private static List<ImportRow> query(EntityManager em, String where, String fileHash) {
        var q = em.createQuery(
                "select i.id, i.fileName, e.board, e.year, e.name, i.importedAt, i.pageCount, i.ocrPages, "
                        + "(select count(q) from Question q where q.importBatch = i) "
                        + "from ImportBatch i left join i.exam e" + where + " order by i.importedAt desc", Object[].class);
        if (fileHash != null) q.setParameter("h", fileHash);
        return q.getResultList().stream()
                .map(r -> new ImportRow((Long) r[0], (String) r[1], (String) r[2], (Integer) r[3], (String) r[4],
                        (LocalDateTime) r[5], (Integer) r[6], (Integer) r[7], ((Number) r[8]).longValue()))
                .toList();
    }

    /** Exclui a importação e, em cascata, suas questões, alternativas e respostas. */
    public void delete(long batchId) {
        Jpa.txVoid(em -> {
            // Remoção explícita para não depender só do ON DELETE CASCADE do banco.
            em.createQuery("delete from Attempt a where a.question.id in "
                            + "(select q.id from Question q where q.importBatch.id = :id)")
                    .setParameter("id", batchId).executeUpdate();
            em.createQuery("delete from Alternative a where a.question.id in "
                            + "(select q.id from Question q where q.importBatch.id = :id)")
                    .setParameter("id", batchId).executeUpdate();
            em.createQuery("delete from Question q where q.importBatch.id = :id")
                    .setParameter("id", batchId).executeUpdate();
            em.createQuery("delete from ImportBatch i where i.id = :id")
                    .setParameter("id", batchId).executeUpdate();
            OrphanCleaner.removeUnused(em);
        });
    }
}
