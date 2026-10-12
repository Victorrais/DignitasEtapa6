package br.com.dignitas.service;

import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.ExamInfo;
import br.com.dignitas.model.QuestionDraft;

/** Questões de exemplo (as mesmas do script SQL original), úteis para testar a aplicação. */
public final class SampleData {
    private SampleData() {
    }

    private static final String[][] QUESTIONS = {
            {"Nas últimas décadas, o avanço das tecnologias digitais transformou significativamente as formas de interação social. Redes sociais, aplicativos de mensagens e plataformas digitais passaram a mediar relações que antes ocorriam predominantemente de forma presencial, gerando novas dinâmicas de comunicação.",
                    "(Adaptado)",
                    "No contexto apresentado, a principal consequência do avanço tecnológico nas relações humanas é:",
                    "A substituição completa das relações presenciais por interações digitais.",
                    "A eliminação das barreiras culturais entre diferentes sociedades.",
                    "A transformação das formas de interação social mediadas pela tecnologia.",
                    "O isolamento total dos indivíduos em ambientes virtuais.",
                    "A redução do uso de ferramentas digitais na comunicação cotidiana."},
            {"A preservação do meio ambiente tornou-se um dos principais desafios do século XXI. A atuação conjunta de governos, empresas e sociedade civil é apontada como fundamental para a mitigação dos impactos ambientais e a promoção do desenvolvimento sustentável.",
                    "(Fragmento)",
                    "A ideia central do texto evidencia que a preservação ambiental:",
                    "Depende exclusivamente de políticas governamentais rígidas.",
                    "É responsabilidade restrita às grandes corporações industriais.",
                    "Exige a participação integrada de diferentes setores da sociedade.",
                    "Pode ser alcançada sem mudanças nos padrões de consumo.",
                    "Não possui relação com o desenvolvimento econômico."},
            {"A educação é reconhecida como um dos principais instrumentos para a redução das desigualdades sociais. Ao promover o acesso ao conhecimento, ela contribui para a formação de cidadãos críticos e conscientes de seus direitos e deveres.",
                    "Disponível em: exemplo.com. Acesso em: 2024.",
                    "De acordo com o texto, a educação exerce papel fundamental porque:",
                    "Garante automaticamente igualdade econômica entre os indivíduos.",
                    "Substitui políticas públicas de inclusão social.",
                    "Contribui para a formação crítica e redução das desigualdades.",
                    "Limita o acesso ao conhecimento a grupos específicos.",
                    "Elimina a necessidade de participação política."},
            {"A intensificação dos fluxos econômicos, culturais e tecnológicos entre países caracteriza o fenômeno da globalização, promovendo maior integração entre diferentes regiões do mundo.",
                    "(Adaptado)",
                    "O texto sugere que a globalização:",
                    "Impede a circulação de informações entre países.",
                    "Promove o isolamento cultural das nações.",
                    "Favorece a integração entre diferentes regiões do mundo.",
                    "Elimina completamente as desigualdades globais.",
                    "Reduz a interdependência econômica entre países."},
            {"O consumo consciente envolve a escolha de produtos e serviços considerando seus impactos ambientais e sociais, incentivando práticas sustentáveis.",
                    "(Fragmento)",
                    "A partir do texto, é correto afirmar que o consumo consciente:",
                    "Ignora os impactos ambientais dos produtos.",
                    "Baseia-se apenas no menor preço disponível.",
                    "Considera os impactos sociais e ambientais das escolhas.",
                    "Elimina completamente o consumo na sociedade.",
                    "É responsabilidade exclusiva das empresas."},
    };

    /** Insere as questões de exemplo que ainda não existirem. Retorna quantas foram inseridas. */
    public static int insert(QuestionService questions) {
        ExamInfo exam = new ExamInfo("Questões de exemplo", "Dignitas", null, null);
        int inserted = 0;
        for (int i = 0; i < QUESTIONS.length; i++) {
            String[] q = QUESTIONS[i];
            QuestionDraft d = new QuestionDraft();
            d.setExam(exam);
            d.setNumber(i + 1);
            d.setSubjectName("Língua Portuguesa");
            d.setBaseText(new BaseTextDraft(null, q[0], q[1]));
            d.setStatement(q[2]);
            for (int a = 0; a < 5; a++) {
                d.addAlternative(String.valueOf((char) ('A' + a)), q[3 + a], a == 2);
            }
            try {
                questions.save(d);
                inserted++;
            } catch (DuplicateQuestionException ignored) {
                // já existe
            }
        }
        return inserted;
    }
}
