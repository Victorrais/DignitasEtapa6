package br.com.dignitas.service;

import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.service.Dtos.CategoryStat;
import br.com.dignitas.service.Dtos.DailyStat;
import br.com.dignitas.service.Dtos.Overview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class StatsService {

    public Overview overview() {
        return Jpa.read(em -> new Overview(
                em.createQuery("select count(q) from Question q", Long.class).getSingleResult(),
                em.createQuery("select count(distinct a.question.id) from Attempt a", Long.class).getSingleResult(),
                em.createQuery("select count(a) from Attempt a", Long.class).getSingleResult(),
                em.createQuery("select count(a) from Attempt a where a.correct = true", Long.class).getSingleResult(),
                em.createQuery("select count(q) from Question q where q.flagged = true", Long.class).getSingleResult(),
                em.createQuery("select count(i) from ImportBatch i", Long.class).getSingleResult()));
    }

    public List<CategoryStat> bySubject() {
        return category("coalesce(s.name, 'Sem disciplina')");
    }

    public List<CategoryStat> byBoard() {
        return category("coalesce(e.board, 'Sem banca')");
    }

    private List<CategoryStat> category(String expr) {
        return Jpa.read(em -> em.createQuery(
                        "select " + expr + ", count(a), sum(case when a.correct = true then 1 else 0 end) "
                                + "from Attempt a join a.question q left join q.subject s left join q.exam e "
                                + "group by " + expr + " order by count(a) desc", Object[].class)
                .getResultList().stream()
                .map(r -> new CategoryStat((String) r[0], ((Number) r[1]).longValue(),
                        r[2] == null ? 0 : ((Number) r[2]).longValue()))
                .toList());
    }

    /** Respostas por dia nos últimos {@code days} dias (dias sem resposta aparecem zerados). */
    public List<DailyStat> daily(int days) {
        LocalDate first = LocalDate.now().minusDays(days - 1L);
        List<Object[]> rows = Jpa.read(em -> em.createQuery(
                        "select a.answeredAt, a.correct from Attempt a where a.answeredAt >= :from", Object[].class)
                .setParameter("from", first.atStartOfDay()).getResultList());
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (int i = 0; i < days; i++) byDay.put(first.plusDays(i), new long[2]);
        for (Object[] r : rows) {
            long[] c = byDay.get(((LocalDateTime) r[0]).toLocalDate());
            if (c == null) continue;
            c[0]++;
            if ((Boolean) r[1]) c[1]++;
        }
        List<DailyStat> result = new ArrayList<>();
        byDay.forEach((d, c) -> result.add(new DailyStat(d, c[0], c[1])));
        return result;
    }
}
