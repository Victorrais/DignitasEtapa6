package br.com.dignitas.service;

import jakarta.persistence.EntityManager;

/** Remove registros que perderam o último vínculo: textos-base e concursos sem questões nem importações. */
final class OrphanCleaner {

    private OrphanCleaner() {
    }

    static void removeUnused(EntityManager em) {
        em.flush();
        em.createQuery("delete from BaseText b where not exists (select 1 from Question q where q.baseText = b)")
                .executeUpdate();
        em.createQuery("delete from Exam e where not exists (select 1 from Question q where q.exam = e) "
                + "and not exists (select 1 from ImportBatch i where i.exam = e)").executeUpdate();
    }
}
