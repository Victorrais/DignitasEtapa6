package br.com.dignitas.service;

import br.com.dignitas.domain.Question;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.service.Dtos.PageResult;
import br.com.dignitas.service.Dtos.QuestionFilter;
import br.com.dignitas.service.Dtos.QuestionRow;
import br.com.dignitas.service.Dtos.Sort;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** Consultas de leitura sobre questões. Não altera dados. */
public final class QuestionQueries {

    public PageResult<QuestionRow> search(QuestionFilter f, int page, int size) {
        return Jpa.read(em -> {
            Where w = where(f);
            long total = w.apply(em.createQuery(
                    "select count(q) from Question q left join q.exam e" + w.sql, Long.class)).getSingleResult();
            TypedQuery<Object[]> q = w.apply(em.createQuery(
                    "select q.id, q.number, q.statement, s.name, e.board, e.year, e.name, q.updatedAt, q.annulled, q.flagged "
                            + "from Question q left join q.subject s left join q.exam e" + w.sql + orderBy(f.sort()),
                    Object[].class));
            q.setFirstResult(page * size).setMaxResults(size);
            List<QuestionRow> rows = q.getResultList().stream().map(r -> new QuestionRow(
                    (Long) r[0], (Integer) r[1], (String) r[2], (String) r[3], (String) r[4], (Integer) r[5],
                    (String) r[6], (LocalDateTime) r[7], (Boolean) r[8], (Boolean) r[9])).toList();
            return new PageResult<>(rows, total, page, size);
        });
    }

    /** Ids das questões que atendem ao filtro (para sortear a próxima questão a resolver). */
    public List<Long> ids(QuestionFilter f) {
        return Jpa.read(em -> {
            Where w = where(f);
            return w.apply(em.createQuery("select q.id from Question q left join q.exam e" + w.sql, Long.class))
                    .getResultList();
        });
    }

    /** Carrega a questão com alternativas, texto-base, disciplina e concurso. */
    public Question loadFull(long id) {
        return Jpa.read(em -> {
            List<Question> list = em.createQuery(
                            "select distinct q from Question q left join fetch q.alternatives "
                                    + "left join fetch q.baseText left join fetch q.subject left join fetch q.exam "
                                    + "where q.id = :id", Question.class)
                    .setParameter("id", id).getResultList();
            if (list.isEmpty()) throw new NoSuchElementException("Questão " + id + " não encontrada");
            return list.getFirst();
        });
    }

    public long count() {
        return Jpa.read(em -> em.createQuery("select count(q) from Question q", Long.class).getSingleResult());
    }

    /** Dos hashes informados, retorna os que já existem no banco. */
    public Set<String> existingHashes(Collection<String> hashes) {
        if (hashes.isEmpty()) return Set.of();
        return Jpa.read(em -> new HashSet<>(em.createQuery(
                        "select q.contentHash from Question q where q.contentHash in :h", String.class)
                .setParameter("h", hashes).getResultList()));
    }

    /** Monta a cláusula where e seus parâmetros a partir do filtro da tela. */
    private static Where where(QuestionFilter f) {
        List<String> c = new ArrayList<>();
        Map<String, Object> p = new HashMap<>();
        if (TextFilters.hasText(f.text())) {
            c.add(TextFilters.STATEMENT_CONTAINS);
            p.put("text", TextFilters.containsPattern(f.text()));
        }
        if (f.subjectId() != null) {
            c.add("q.subject.id = :subject");
            p.put("subject", f.subjectId());
        }
        if (TextFilters.hasText(f.board())) {
            c.add("e.board = :board");
            p.put("board", f.board());
        }
        if (f.year() != null) {
            c.add("e.year = :year");
            p.put("year", f.year());
        }
        if (f.examId() != null) {
            c.add("e.id = :exam");
            p.put("exam", f.examId());
        }
        if (f.skipAnnulled()) c.add("q.annulled = false");
        if (f.status() != null) {
            switch (f.status()) {
                case UNANSWERED -> c.add("not exists (select 1 from Attempt a where a.question = q)");
                case WRONG -> c.add("exists (select 1 from Attempt a where a.question = q and a.correct = false)");
                case FLAGGED -> c.add("q.flagged = true");
                case ALL -> {
                }
            }
        }
        return new Where(c.isEmpty() ? "" : " where " + String.join(" and ", c), p);
    }

    private static String orderBy(Sort sort) {
        if (sort == null) sort = Sort.RECENT;
        return switch (sort) {
            case RECENT -> " order by q.updatedAt desc, q.id desc";
            case OLDEST -> " order by q.updatedAt asc, q.id asc";
            case NUMBER -> " order by e.name, q.number, q.id";
        };
    }

    /** Cláusula JPQL e seus parâmetros, aplicados juntos a uma consulta. */
    private static final class Where {
        final String sql;
        final Map<String, Object> params;

        Where(String sql, Map<String, Object> params) {
            this.sql = sql;
            this.params = params;
        }

        <T> TypedQuery<T> apply(TypedQuery<T> q) {
            params.forEach(q::setParameter);
            return q;
        }
    }
}
