package br.com.dignitas;

import br.com.dignitas.domain.Alternative;
import br.com.dignitas.domain.Question;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.persistence.Jpa;
import br.com.dignitas.persistence.SchemaInstaller;
import br.com.dignitas.service.AlreadyImportedException;
import br.com.dignitas.service.AttemptService;
import br.com.dignitas.service.CatalogService;
import br.com.dignitas.service.DuplicateQuestionException;
import br.com.dignitas.service.ImportService;
import br.com.dignitas.service.QuestionMapper;
import br.com.dignitas.service.QuestionService;
import br.com.dignitas.service.SampleData;
import br.com.dignitas.service.StatsService;
import br.com.dignitas.service.Dtos.QuestionFilter;
import br.com.dignitas.service.Dtos.ResultFilter;
import br.com.dignitas.service.Dtos.Sort;
import br.com.dignitas.service.Dtos.Status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Autoverificação das regras de negócio, executada pelo método {@code main()}.
 * Usa H2 em memória com o {@code schema.sql} real, sem depender de interface gráfica.
 * Sai com código 1 se alguma verificação falhar.
 */
public final class SelfTest {

    private static int passed;
    private static int failed;

    private final QuestionService questions = new QuestionService();
    private final AttemptService attempts = new AttemptService();
    private final ImportService imports = new ImportService();
    private final StatsService stats = new StatsService();
    private final CatalogService catalog = new CatalogService();

    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection keepAlive = DriverManager.getConnection(url, "sa", "")) {
            SchemaInstaller.install(keepAlive);
            // O persistence.xml aponta para MySQL; o driver H2 precisa ser informado explicitamente.
            Jpa.init(Map.of(
                    "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                    "jakarta.persistence.jdbc.url", url,
                    "jakarta.persistence.jdbc.user", "sa",
                    "jakarta.persistence.jdbc.password", ""));
            try {
                new SelfTest().runAll();
            } finally {
                Jpa.shutdown();
            }
        }
        System.out.printf("%n%d verificações aprovadas, %d falharam.%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    private void runAll() {
        sampleDataIsInsertedOnceAndLoadsFully();
        draftRoundTripKeepsContentHash();
        duplicateQuestionIsRejected();
        invalidDraftIsRejected();
        answersAndHistory();
        filtersByStatus();
        importIsNotRecordedTwice();
        deletingLastQuestionRemovesOrphanExam();
    }

    private void sampleDataIsInsertedOnceAndLoadsFully() {
        check("dados de exemplo são inseridos", SampleData.insert(questions) == 5);
        check("segunda inserção detecta duplicidade", SampleData.insert(questions) == 0);
        check("contagem após inserção", questions.count() == 5);

        long id = questions.search(QuestionFilter.all(), 0, 10).items().getFirst().id();
        Question q = questions.loadFull(id);
        check("questão carrega cinco alternativas", q.getAlternatives().size() == 5);
        check("gabarito é a alternativa C",
                q.correctAlternative().map(Alternative::getLetter).orElse("").equals("C"));
        check("texto-base é carregado", q.getBaseText() != null);
        check("disciplina é carregada", "Língua Portuguesa".equals(q.getSubject().getName()));
        check("concurso é carregado", "Dignitas".equals(q.getExam().getBoard()));
    }

    /** O hash de conteúdo deve ser o mesmo antes e depois de passar pelo mapeamento entidade ↔ rascunho. */
    private void draftRoundTripKeepsContentHash() {
        long id = questions.save(draft("Quanto é 2 + 2?", "Matemática", 1));
        Question q = questions.loadFull(id);
        QuestionDraft back = QuestionMapper.toDraft(q);
        check("rascunho recuperado preserva o hash", back.contentHash().equals(q.getContentHash()));
    }

    private void duplicateQuestionIsRejected() {
        questions.save(draft("Qual é a capital do Brasil?", "Geografia", 0));
        boolean rejected = false;
        try {
            questions.save(draft("Qual é a capital do Brasil?", "Geografia", 0));
        } catch (DuplicateQuestionException e) {
            rejected = true;
        }
        check("questão repetida é recusada", rejected);
    }

    private void invalidDraftIsRejected() {
        boolean rejected = false;
        try {
            questions.save(new QuestionDraft());
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check("rascunho inválido é recusado", rejected);
    }

    private void answersAndHistory() {
        long id = questions.save(draft("Quanto é 3 + 3?", "Matemática", 1));
        Question q = questions.loadFull(id);
        long wrong = q.getAlternatives().get(0).getId();
        long right = q.getAlternatives().get(1).getId();

        check("resposta errada é marcada como errada", !attempts.answer(id, wrong).correct());
        check("resposta certa é marcada como certa", attempts.answer(id, right).correct());
        check("histórico de erradas tem uma entrada",
                attempts.history(null, ResultFilter.WRONG, 0, 10).total() == 1);
        check("histórico de todas tem duas entradas",
                attempts.history("3 + 3", ResultFilter.ALL, 0, 10).total() == 2);
        check("estatística registra duas respostas", stats.overview().attempts() == 2);
    }

    private void filtersByStatus() {
        long id = questions.save(draft("Quanto é 4 + 4?", "Matemática", 0));
        long wrong = questions.loadFull(id).getAlternatives().get(3).getId();
        attempts.answer(id, wrong);

        QuestionFilter unanswered = new QuestionFilter(null, null, null, null, null, Status.UNANSWERED,
                Sort.RECENT, false);
        QuestionFilter wrongOnes = new QuestionFilter(null, null, null, null, null, Status.WRONG,
                Sort.RECENT, false);
        check("filtro de não respondidas exclui a questão respondida",
                questions.search(unanswered, 0, 100).items().stream().noneMatch(r -> r.id() == id));
        check("filtro de já errei inclui a questão respondida errada",
                questions.search(wrongOnes, 0, 100).items().stream().anyMatch(r -> r.id() == id));
    }

    private void importIsNotRecordedTwice() {
        ExamInfo exam = new ExamInfo("Concurso Teste", "Banca", 2024, null);
        QuestionDraft d = draft("Enunciado da importação", "Português", 2);
        d.setExam(exam);
        var summary = imports.save(exam, "prova.pdf", "hash-arquivo-1", 3, 0, List.of(d));
        check("importação grava a questão", summary.saved() == 1);

        boolean rejected = false;
        try {
            imports.save(exam, "prova.pdf", "hash-arquivo-1", 3, 0, List.of(draft("Outro enunciado", "Português", 0)));
        } catch (AlreadyImportedException e) {
            rejected = true;
        }
        check("mesmo arquivo não é importado duas vezes", rejected);
        check("findByFileHash encontra a importação", imports.findByFileHash("hash-arquivo-1").isPresent());
    }

    private void deletingLastQuestionRemovesOrphanExam() {
        ExamInfo exam = new ExamInfo("Concurso Órfão", "Banca X", 1999, null);
        QuestionDraft d = draft("Enunciado órfão", "Direito", 0);
        d.setExam(exam);
        long id = questions.save(d);
        check("concurso existe antes da exclusão",
                catalog.boards().contains("Banca X"));

        questions.delete(id);
        check("concurso sem questões é removido ao excluir a última", !catalog.boards().contains("Banca X"));
    }

    private static QuestionDraft draft(String statement, String subject, int correct) {
        QuestionDraft d = new QuestionDraft();
        d.setStatement(statement);
        d.setSubjectName(subject);
        for (int i = 0; i < 5; i++) {
            d.addAlternative(String.valueOf((char) ('A' + i)), statement + " alt " + i, i == correct);
        }
        return d;
    }

    private static void check(String description, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  [ok]    " + description);
        } else {
            failed++;
            System.out.println("  [FALHA] " + description);
        }
    }
}
