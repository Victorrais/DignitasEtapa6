package br.com.dignitas.service;

import br.com.dignitas.domain.Alternative;
import br.com.dignitas.domain.Attempt;
import br.com.dignitas.domain.Question;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.service.Dtos.AnswerResult;
import br.com.dignitas.service.Dtos.HistoryRow;
import br.com.dignitas.service.Dtos.PageResult;
import br.com.dignitas.service.Dtos.ResultFilter;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class AttemptService {

    /** Registra a resposta do usuário e informa se acertou. */
    public AnswerResult answer(long questionId, long alternativeId) {
        return Jpa.tx(em -> {
            Question q = em.find(Question.class, questionId);
            Alternative chosen = em.find(Alternative.class, alternativeId);
            if (q == null || chosen == null || !chosen.getQuestion().getId().equals(q.getId())) {
                throw new NoSuchElementException("Alternativa inválida para a questão");
            }
            String correctLetter = q.correctAlternative().map(Alternative::getLetter).orElse(null);
            boolean correct = q.isAnnulled() || chosen.isAnswer();
            em.persist(new Attempt(q, chosen, correct));
            return new AnswerResult(correct, correctLetter);
        });
    }

    public PageResult<HistoryRow> history(String text, ResultFilter filter, int page, int size) {
        return Jpa.read(em -> {
            List<String> c = new ArrayList<>();
            Map<String, Object> p = new HashMap<>();
            if (TextFilters.hasText(text)) {
                c.add(TextFilters.STATEMENT_CONTAINS);
                p.put("text", TextFilters.containsPattern(text));
            }
            if (filter == ResultFilter.CORRECT) c.add("a.correct = true");
            if (filter == ResultFilter.WRONG) c.add("a.correct = false");
            String from = " from Attempt a join a.question q left join q.exam e"
                    + (c.isEmpty() ? "" : " where " + String.join(" and ", c));

            TypedQuery<Long> count = em.createQuery("select count(a)" + from, Long.class);
            p.forEach(count::setParameter);
            long total = count.getSingleResult();

            TypedQuery<Object[]> q = em.createQuery(
                    "select a.id, q.id, q.number, q.statement, e.board, e.year, e.name, a.chosenLetter, a.correct, a.answeredAt"
                            + from + " order by a.answeredAt desc, a.id desc", Object[].class);
            p.forEach(q::setParameter);
            q.setFirstResult(page * size).setMaxResults(size);
            List<HistoryRow> rows = q.getResultList().stream().map(r -> new HistoryRow(
                    (Long) r[0], (Long) r[1], (Integer) r[2], (String) r[3], (String) r[4], (Integer) r[5],
                    (String) r[6], (String) r[7], (Boolean) r[8], (LocalDateTime) r[9])).toList();
            return new PageResult<>(rows, total, page, size);
        });
    }

    public void deleteAttempt(long attemptId) {
        Jpa.txVoid(em -> em.createQuery("delete from Attempt a where a.id = :id")
                .setParameter("id", attemptId).executeUpdate());
    }
}
