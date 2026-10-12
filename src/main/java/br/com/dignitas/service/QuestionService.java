package br.com.dignitas.service;

import br.com.dignitas.domain.Question;
import br.com.dignitas.domain.Subject;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.service.Dtos.PageResult;
import br.com.dignitas.service.Dtos.QuestionFilter;
import br.com.dignitas.service.Dtos.QuestionRow;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Comandos sobre questões: salvar, excluir e marcar para revisão.
 * As leituras são delegadas a {@link QuestionQueries} e {@link CatalogService}; estes métodos
 * ficam aqui como fachada para quem já usa este serviço.
 */
public class QuestionService {
    private final QuestionQueries queries = new QuestionQueries();
    private final CatalogService catalog = new CatalogService();

    // ---------- Leituras (delegadas) ----------

    public PageResult<QuestionRow> search(QuestionFilter f, int page, int size) {
        return queries.search(f, page, size);
    }

    public List<Long> ids(QuestionFilter f) {
        return queries.ids(f);
    }

    public Question loadFull(long id) {
        return queries.loadFull(id);
    }

    public long count() {
        return queries.count();
    }

    public Set<String> existingHashes(Collection<String> hashes) {
        return queries.existingHashes(hashes);
    }

    public QuestionDraft toDraft(Question q) {
        return QuestionMapper.toDraft(q);
    }

    public List<Subject> subjects() {
        return catalog.subjects();
    }

    public List<String> boards() {
        return catalog.boards();
    }

    public List<Integer> years() {
        return catalog.years();
    }

    // ---------- Comandos ----------

    /** Cria ou atualiza uma questão a partir do rascunho. Retorna o id. */
    public long save(QuestionDraft draft) {
        List<String> errors = draft.validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return Jpa.tx(em -> {
            Question q = draft.getId() == null ? new Question() : em.find(Question.class, draft.getId());
            if (q == null) throw new NoSuchElementException("Questão não encontrada");
            String hash = draft.contentHash();
            Long clash = em.createQuery("select count(q) from Question q where q.contentHash = :h and q.id <> :id", Long.class)
                    .setParameter("h", hash).setParameter("id", q.getId() == null ? -1L : q.getId()).getSingleResult();
            if (clash > 0) throw new DuplicateQuestionException();
            q.setExam(draft.getExam() == null || draft.getExam().isEmpty() ? null
                    : CatalogService.findOrCreateExam(em, draft.getExam()));
            QuestionMapper.apply(em, q, draft, hash, new IdentityHashMap<>());
            if (q.getId() == null) em.persist(q);
            em.flush();
            return q.getId();
        });
    }

    public void delete(long id) {
        Jpa.txVoid(em -> {
            Question q = em.find(Question.class, id);
            if (q != null) em.remove(q);
            OrphanCleaner.removeUnused(em);
        });
    }

    public void setFlagged(long id, boolean flagged) {
        Jpa.txVoid(em -> em.createQuery("update Question q set q.flagged = :f where q.id = :id")
                .setParameter("f", flagged).setParameter("id", id).executeUpdate());
    }
}
