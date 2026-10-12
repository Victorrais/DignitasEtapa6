package br.com.dignitas.service;

import br.com.dignitas.domain.Exam;
import br.com.dignitas.domain.Subject;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.Texts;
import br.com.dignitas.persistence.Jpa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;

/**
 * Disciplinas e concursos: criação sem duplicar registros e listas para os filtros.
 * Responsabilidade única: o catálogo de referência, não as questões.
 */
public final class CatalogService {

    public List<Subject> subjects() {
        return Jpa.read(em -> em.createQuery("select s from Subject s order by s.name", Subject.class).getResultList());
    }

    public List<String> boards() {
        return Jpa.read(em -> em.createQuery(
                "select distinct e.board from Exam e where e.board is not null order by e.board", String.class).getResultList());
    }

    public List<Integer> years() {
        return Jpa.read(em -> em.createQuery(
                "select distinct e.year from Exam e where e.year is not null order by e.year desc", Integer.class).getResultList());
    }

    /** Busca a disciplina pelo nome (sem diferenciar maiúsculas) ou cria uma nova. Deve rodar dentro de uma transação. */
    static Subject findOrCreateSubject(EntityManager em, String name) {
        List<Subject> found = em.createQuery("select s from Subject s where lower(s.name) = lower(:n)", Subject.class)
                .setParameter("n", name).getResultList();
        if (!found.isEmpty()) return found.getFirst();
        Subject s = new Subject(name);
        em.persist(s);
        return s;
    }

    /** Busca o concurso pelo nome, banca e ano, ou cria um novo. Deve rodar dentro de uma transação. */
    static Exam findOrCreateExam(EntityManager em, ExamInfo info) {
        String name = info.nameOrDefault();
        String board = Texts.blankToNull(info.board());
        String jpql = "select e from Exam e where e.name = :n"
                + (board == null ? " and e.board is null" : " and e.board = :b")
                + (info.year() == null ? " and e.year is null" : " and e.year = :y");
        TypedQuery<Exam> q = em.createQuery(jpql, Exam.class).setParameter("n", name);
        if (board != null) q.setParameter("b", board);
        if (info.year() != null) q.setParameter("y", info.year());
        List<Exam> found = q.getResultList();
        if (!found.isEmpty()) return found.getFirst();
        Exam e = new Exam(name, board, info.year(), Texts.blankToNull(info.role()));
        em.persist(e);
        return e;
    }
}
