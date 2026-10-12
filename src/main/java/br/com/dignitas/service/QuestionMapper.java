package br.com.dignitas.service;

import br.com.dignitas.domain.Alternative;
import br.com.dignitas.domain.BaseText;
import br.com.dignitas.domain.Exam;
import br.com.dignitas.domain.Question;
import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.QuestionDraft.AlternativeDraft;
import br.com.dignitas.model.Texts;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Map;

/**
 * Converte entre a entidade {@link Question} e o rascunho {@link QuestionDraft}, nos dois sentidos.
 * É o único lugar que conhece essa correspondência, usado pelo editor e pela importação.
 */
public final class QuestionMapper {

    private QuestionMapper() {
    }

    /** Entidade persistida para rascunho editável. */
    public static QuestionDraft toDraft(Question q) {
        QuestionDraft d = new QuestionDraft();
        d.setId(q.getId());
        d.setNumber(q.getNumber());
        Exam e = q.getExam();
        if (e != null) d.setExam(new ExamInfo(e.getName(), e.getBoard(), e.getYear(), e.getRole()));
        d.setSubjectName(q.getSubject() == null ? null : q.getSubject().getName());
        BaseText bt = q.getBaseText();
        if (bt != null) {
            BaseTextDraft b = new BaseTextDraft(bt.getId(), bt.getContent(), bt.getSource());
            b.setImage(bt.getImage());
            d.setBaseText(b);
        }
        d.setStatement(q.getStatement());
        q.getAlternatives().forEach(a -> d.addAlternative(a.getLetter(), a.getStatement(), a.isAnswer()).setImage(a.getImage()));
        d.setAnnulled(q.isAnnulled());
        d.setTrueFalse(isTrueFalse(q.getAlternatives()));
        d.setImage(q.getImage());
        return d;
    }

    /** Questão de certo ou errado: exatamente duas alternativas, C e E. */
    private static boolean isTrueFalse(List<Alternative> alternatives) {
        return alternatives.size() == 2
                && "C".equals(alternatives.get(0).getLetter())
                && "E".equals(alternatives.get(1).getLetter());
    }

    /**
     * Copia o rascunho para a entidade. As alternativas são atualizadas no lugar, para preservar
     * os ids referenciados pelo histórico de respostas. Não trata o concurso: quem chama decide.
     *
     * @param baseTexts textos-base já copiados nesta operação, para reaproveitar o mesmo objeto
     */
    public static void apply(EntityManager em, Question q, QuestionDraft d, String hash,
                             Map<BaseTextDraft, BaseText> baseTexts) {
        q.setNumber(d.getNumber());
        q.setStatement(nz(d.getStatement()));
        q.setContentHash(hash);
        q.setAnnulled(d.isAnnulled());
        q.setImage(d.getImage());
        q.setSubject(Texts.isBlank(d.getSubjectName()) ? null
                : CatalogService.findOrCreateSubject(em, d.getSubjectName().strip()));
        q.setBaseText(resolveBaseText(em, d.getBaseText(), baseTexts));
        applyAlternatives(q, d.getAlternatives());
    }

    /** Resolve o texto-base do rascunho: reaproveita o já processado, atualiza o existente ou cria um novo. */
    private static BaseText resolveBaseText(EntityManager em, BaseTextDraft btd, Map<BaseTextDraft, BaseText> baseTexts) {
        if (btd == null || btd.isEmpty()) return null;
        BaseText bt = baseTexts.get(btd);
        if (bt != null) return bt;
        bt = btd.getId() != null ? em.find(BaseText.class, btd.getId()) : null;
        if (bt == null) {
            bt = new BaseText();
            copy(btd, bt);
            em.persist(bt);
        } else {
            copy(btd, bt);
        }
        baseTexts.put(btd, bt);
        return bt;
    }

    private static void applyAlternatives(Question q, List<AlternativeDraft> wanted) {
        List<Alternative> current = q.getAlternatives();
        for (int i = 0; i < wanted.size(); i++) {
            AlternativeDraft w = wanted.get(i);
            if (i < current.size()) {
                Alternative a = current.get(i);
                a.setLetter(w.getLetter());
                a.setStatement(nz(w.getText()));
                a.setAnswer(w.isCorrect());
                a.setImage(w.getImage());
            } else {
                Alternative a = new Alternative(w.getLetter(), nz(w.getText()), w.isCorrect());
                a.setImage(w.getImage());
                q.addAlternative(a);
            }
        }
        while (current.size() > wanted.size()) {
            current.removeLast();
        }
    }

    private static void copy(BaseTextDraft from, BaseText to) {
        to.setContent(nz(from.getContent()));
        to.setSource(Texts.blankToNull(from.getSource()));
        to.setImage(from.getImage());
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }
}
