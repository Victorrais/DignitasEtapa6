package br.com.dignitas.extraction;

import br.com.dignitas.model.BaseTextDraft;
import br.com.dignitas.model.QuestionDraft;
import br.com.dignitas.model.QuestionDraft.AlternativeDraft;
import br.com.dignitas.model.QuestionDraft.Region;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Divide as linhas (em ordem de leitura) em questões, sem depender de um modelo fixo de prova.
 * <p>
 * Funciona como uma máquina de estados guiada por padrões comuns às bancas brasileiras:
 * rótulos de questão ("Questão 12", "QUESTÃO 12", "12."), alternativas ("(A)", "A)", "a." ou só a letra,
 * como no ENEM), textos de apoio compartilhados ("Texto I", "Texto para as questões de 06 a 10"),
 * fontes ("Disponível em:", "(Adaptado)"), títulos de disciplina e figuras.
 * <p>
 * Antes da máquina de estados, duas varreduras olham o documento inteiro:
 * <ul>
 *   <li>sequências de alternativas só com a letra (A, B, C, D, E alinhadas), para não confundir
 *       "A autora conclui…" (enunciado) com a alternativa A;</li>
 *   <li>números de linha à margem dos textos ("1", "2 que eu ia…", "3 burocráticas…"), que são removidos.</li>
 * </ul>
 * O resultado sempre passa pela tela de revisão: a meta é acertar a maior parte e sinalizar o duvidoso.
 */
public class QuestionSegmenter {

    static final Pattern QUESTION_LABEL = Pattern.compile(
            "^(?:[Qq][Uu][Ee][Ss][Tt][ÃãAa][Oo]|ITEM|Item)\\s*(?:N[º°o.]*\\s*)?(\\d{1,3})\\b\\s*[.:\\-–—)]?\\s*(.*)$");
    static final Pattern NUMBERED = Pattern.compile("^(\\d{1,3})\\s*[.)\\-–—]\\s*(\\S.*)$");
    static final Pattern NUMBER_ONLY = Pattern.compile("^(\\d{1,3})[.)]?$");
    static final Pattern NUMBER_SPACE = Pattern.compile("^(\\d{1,3})\\s+([A-ZÀ-Ú\"“(].*)$");
    static final Pattern ALTERNATIVE = Pattern.compile("^\\(?([A-Ea-e])\\s*[).\\-–—:]\\s*(.*)$");
    static final Pattern BARE_ALTERNATIVE = Pattern.compile("^([A-E])(?:\\s+(\\S.*))?$");
    static final Pattern INLINE_ALTERNATIVE = Pattern.compile("(?:^|\\s)\\(?([A-E])\\)\\s*");
    static final Pattern TEXT_LABEL = Pattern.compile("^(?:TEXTO|Texto)\\s+(?:[IVXL]+|\\d{1,2}|[A-Z]{1,3}\\d[A-Z0-9]*)\\b.*$");
    static final Pattern READ_INSTRUCTION = Pattern.compile(
            "(?i)^(?:leia|considere|observe|analise)\\s+(?:atentamente\\s+)?(?:o|a|os|as)\\s+"
                    + "(?:texto|textos|fragmento|trecho|poema|charge|tirinha|gr[áa]fico|tabela|quadro|excerto|figura)\\b.*");
    static final Pattern FOR_QUESTIONS = Pattern.compile(
            "(?i).*\\b(?:para\\s+responder|para\\s+julgar|responda|julgue)\\b.*\\bquest(?:ões|oes|ão|ao)\\b.*");
    static final Pattern RANGE = Pattern.compile(
            "(?i)quest(?:ões|oes)\\s+(?:de\\s+)?(?:n[º°os.]*\\s*)?(\\d{1,3})\\s*(?:a|à|e|até|-|–)\\s*(\\d{1,3})");
    /** Palavras que indicam que uma faixa de questões se refere a um texto de apoio. */
    static final Pattern TEXT_WORDS = Pattern.compile(
            "(?i)(?<![\\p{L}])(texto|textos|leia|fragmento|charge|tirinha|imagem|gr[áa]fico|poema)(?![\\p{L}])");
    static final Pattern LANGUAGE_OPTION = Pattern.compile(
            "(?i)quest(?:ões|oes)\\s+de\\s+(\\d{1,3})\\s+a\\s+(\\d{1,3})\\s*\\(\\s*op[çc][ãa]o\\s+(ingl[êe]s|espanhol)\\s*\\)");
    static final Pattern SOURCE = Pattern.compile(
            "(?i)^(?:dispon[íi]vel\\s+em|fonte\\s*:|in\\s*:|\\(?adaptado|\\(fragmento|\\(trecho|\\(com adapta|"
                    + "adaptado de|extra[íi]do de|acesso em).*");
    /** Referência bibliográfica no formato "SOBRENOME, N. Título…". */
    static final Pattern CITATION = Pattern.compile(
            "^[A-ZÀ-Ú][A-ZÀ-Ú'\\-]+(?:\\s+[A-ZÀ-Ú][A-ZÀ-Ú'\\-]+)*,\\s+[A-ZÀ-Ú][A-Za-zÀ-ú]*\\..*");
    static final Pattern SOURCE_CONTINUATION = Pattern.compile(
            "(?i).*(acesso em|https?://|www\\.|\\.com|\\.br|\\d{4}\\.?|\\(adaptado\\)\\.?|\\(fragmento\\)\\.?)\\s*\\)?\\.?$");
    /** "ARISTÓTELES. Política. …", "PAULINHO DA VIOLA. Foi um rio…" (só vale em corpo de letra menor). */
    static final Pattern CITATION_UPPER = Pattern.compile(
            "^[A-ZÀ-Ú][A-ZÀ-Ú'\\-]{2,}(?:\\s+(?:DA|DE|DO|DOS|DAS|[A-ZÀ-Ú][A-ZÀ-Ú'\\-]+))*\\.\\s+\\S.*");
    /** "NACHO VEGAS. Disponível em: …", "BRASIL. Lei n. … Disponível em: …", "Revista X, n. 31 (adaptado)." */
    static final Pattern SOURCE_ANYWHERE = Pattern.compile(
            "(?iu).*(\\bdispon[íi]vel\\s+em\\s*:|\\((adaptado|fragmento|adapta[çc][ãa]o|trecho)\\)\\.?\\s*$).*");
    static final Pattern STOP = Pattern.compile(
            "(?iu)^(proposta de reda[çc][ãa]o|instru[çc][õo]es para a reda[çc][ãa]o|rascunho|folha de reda[çc][ãa]o)\\b.*");
    static final Pattern IMAGE_HINT = Pattern.compile(
            "(?i)\\b(figura|gr[áa]fico|imagem|charge|tirinha|mapa|ilustra[çc][ãa]o|quadro a seguir|tabela a seguir)\\b");
    static final Pattern LINE_NUMBER = Pattern.compile("^(\\d{1,3})(?:\\s+(.*))?$");
    private static final String[] SUBJECT_KEYWORDS = {
            "lingua", "portugu", "matematic", "raciocinio", "logic", "direito", "legisla", "conhecimentos",
            "informatica", "atualidades", "administra", "contabil", "ingles", "espanhol", "historia", "geografia",
            "fisica", "quimica", "biologia", "etica", "nocoes", "estatistica", "economia", "arquivologia",
            "redacao", "especific", "gerais", "basicos", "saude", "pedagog", "educacao", "financ", "auditoria",
            "seguranca", "ciencias", "tecnologias", "gestao", "politica", "linguagens"};

    private enum Mode {NONE, BASE, STATEMENT, ALTERNATIVES}

    private static final class Open {
        final QuestionDraft draft = new QuestionDraft();
        final StringBuilder statement = new StringBuilder();
        final List<StringBuilder> alternatives = new ArrayList<>();
        final List<TextLine> altLines = new ArrayList<>();
        final List<TextLine> lines = new ArrayList<>();
        /** Figuras e a posição do enunciado em que apareceram. */
        final List<TextLine> figures = new ArrayList<>();
        final List<Integer> figurePositions = new ArrayList<>();
        final StringBuilder source = new StringBuilder();
        int splitAt = -1;
        boolean inSource;
        TextLine lastLine;
        boolean useLowercase;
    }

    private static final class OpenBase {
        final BaseTextDraft draft = new BaseTextDraft();
        final StringBuilder content = new StringBuilder();
        final StringBuilder source = new StringBuilder();
        Integer from;
        Integer to;
        boolean inSource;
        TextLine lastLine;

        boolean hasContent() {
            return !content.isEmpty() || !draft.getFigures().isEmpty();
        }
    }

    private final List<QuestionDraft> result = new ArrayList<>();
    private Mode mode = Mode.NONE;
    private Open current;
    private OpenBase base;
    private String subject;
    private String optionSubject;
    private int optionTo;
    private int lastNumber;
    private boolean multipleChoiceSeen;
    /** A prova usa rótulos ("Questão 12"): números soltos não iniciam questões. */
    private boolean labelStyle;
    /** Depois de "PROPOSTA DE REDAÇÃO" etc.: ignora tudo até um título de área ou rótulo de questão. */
    private boolean halted;
    private double sourceFont;
    private final Map<TextLine, Character> bareAlternatives = new IdentityHashMap<>();
    private final Map<TextLine, String> numberedLines = new IdentityHashMap<>();

    public List<QuestionDraft> segment(List<PageContent> pages) {
        result.clear();
        mode = Mode.NONE;
        current = null;
        base = null;
        subject = null;
        optionSubject = null;
        lastNumber = 0;
        multipleChoiceSeen = false;
        halted = false;

        List<TextLine> all = new ArrayList<>();
        for (PageContent p : pages) all.addAll(p.lines());
        labelStyle = all.stream().filter(l -> QUESTION_LABEL.matcher(l.text().strip()).matches()).count() >= 3;
        findBareAlternatives(all);
        findLineNumbers(all);

        for (TextLine l : all) accept(l);
        finish();
        return new ArrayList<>(result);
    }

    // ---------- Varreduras prévias ----------

    /** Marca linhas "A …", "B …", … "E …" alinhadas e em sequência como alternativas. */
    private void findBareAlternatives(List<TextLine> all) {
        bareAlternatives.clear();
        for (int i = 0; i < all.size(); i++) {
            if (bareLetter(all.get(i)) != 'A') continue;
            List<Integer> run = new ArrayList<>(List.of(i));
            int last = i;
            boolean ok = true;
            for (char want = 'B'; want <= 'E' && ok; want++) {
                int found = -1;
                for (int j = last + 1; j < Math.min(all.size(), last + 40); j++) {
                    TextLine l = all.get(j);
                    if (QUESTION_LABEL.matcher(l.text().strip()).matches()) break;
                    char c = bareLetter(l);
                    // Outra "A" alinhada antes da "B": a sequência começa nela.
                    if (want == 'B' && c == 'A' && aligned(all.get(last), l)) {
                        ok = false;
                        break;
                    }
                    if (c == want && aligned(all.get(last), l)) {
                        found = j;
                        break;
                    }
                }
                if (!ok || found < 0) break;
                run.add(found);
                last = found;
            }
            if (ok && run.size() >= 4) {
                for (int k = 0; k < run.size(); k++) bareAlternatives.put(all.get(run.get(k)), (char) ('A' + k));
            }
        }
    }

    private static char bareLetter(TextLine l) {
        if (l.isFigure()) return 0;
        Matcher m = BARE_ALTERNATIVE.matcher(l.text().strip());
        return m.matches() ? m.group(1).charAt(0) : 0;
    }

    /** Mesma margem esquerda, ou a sequência continua em outra coluna/página. */
    private static boolean aligned(TextLine prev, TextLine next) {
        if (next.page() != prev.page()) return true;
        if (Math.abs(next.x() - prev.x()) <= 6) return true;
        return next.y() < prev.y() - 30; // subiu: nova coluna
    }

    /** Encontra números de linha à margem dos textos (sequências crescentes de 3 ou mais). */
    private void findLineNumbers(List<TextLine> all) {
        numberedLines.clear();
        if (!labelStyle) return;
        for (int i = 0; i < all.size(); i++) {
            Integer n = lineNumber(all.get(i));
            if (n == null || numberedLines.containsKey(all.get(i))) continue;
            List<Integer> run = new ArrayList<>(List.of(i));
            int expected = n + 1;
            int misses = 0;
            for (int j = i + 1; j < all.size() && misses <= 2; j++) {
                Integer m = lineNumber(all.get(j));
                if (m != null && m == expected && all.get(j).page() == all.get(i).page()) {
                    run.add(j);
                    expected++;
                    misses = 0;
                } else {
                    misses++;
                }
            }
            if (run.size() >= 3) {
                for (int k : run) {
                    Matcher m = LINE_NUMBER.matcher(all.get(k).text().strip());
                    if (m.matches()) numberedLines.put(all.get(k), m.group(2) == null ? "" : m.group(2));
                }
            }
        }
    }

    private static Integer lineNumber(TextLine l) {
        if (l.isFigure()) return null;
        Matcher m = LINE_NUMBER.matcher(l.text().strip());
        return m.matches() ? Integer.parseInt(m.group(1)) : null;
    }

    // ---------- Máquina de estados ----------

    private void accept(TextLine line) {
        if (line.isFigure()) {
            acceptFigure(line);
            return;
        }
        String t = line.text().strip();
        if (numberedLines.containsKey(line)) {
            t = numberedLines.get(line).strip();
            line = line.withText(t);
        }
        if (t.isEmpty()) return;

        if (STOP.matcher(t).matches()) {
            finish();
            base = null;
            mode = Mode.NONE;
            halted = true;
            return;
        }

        Matcher option = LANGUAGE_OPTION.matcher(t);
        if (option.find()) {
            finish();
            base = null;
            optionSubject = option.group(3).toLowerCase(Locale.ROOT).startsWith("ingl") ? "Língua Inglesa" : "Língua Espanhola";
            optionTo = Integer.parseInt(option.group(2));
            lastNumber = Integer.parseInt(option.group(1)) - 1;
            mode = Mode.NONE;
            return;
        }

        if (isSubjectHeading(line, t)) {
            finish();
            halted = false;
            base = null;
            subject = toTitleCase(t.replaceFirst("(?i)^prova\\s+de\\s+", ""));
            optionSubject = null;
            mode = Mode.NONE;
            return;
        }

        Matcher q = matchQuestionStart(line, t);
        if (halted && q == null) return;
        if (q == null && tryBaseTextStart(line, t)) return;
        if (q != null) {
            halted = false;
            finish();
            current = new Open();
            int number = Integer.parseInt(q.group(1));
            current.draft.setNumber(number);
            if (optionSubject != null && number > optionTo) optionSubject = null;
            current.draft.setSubjectName(optionSubject != null ? optionSubject : subject);
            mode = Mode.STATEMENT;
            String rest = q.groupCount() >= 2 && q.group(2) != null ? q.group(2).strip() : "";
            if (!rest.isEmpty()) appendToStatement(line.withText(rest));
            current.lines.add(line);
            return;
        }

        if (current != null && (mode == Mode.STATEMENT || mode == Mode.ALTERNATIVES) && tryAlternatives(line, t)) {
            return;
        }

        if (mode == Mode.BASE && base != null && isSource(line, t, base.inSource, base.lastLine)) {
            if (!base.inSource) sourceFont = line.fontSize();
            if (!base.source.isEmpty()) base.source.append(' ');
            base.source.append(t);
            base.inSource = true;
            base.lastLine = line;
            return;
        }

        if (mode == Mode.STATEMENT && current != null && isSource(line, t, current.inSource, current.lastLine)) {
            if (!current.inSource) sourceFont = line.fontSize();
            if (!current.inSource && !current.source.isEmpty() && current.splitAt >= 0) {
                // Segundo texto com fonte própria (TEXTO I … TEXTO II): a fonte anterior volta ao texto.
                current.statement.insert(current.splitAt, "\n" + current.source + "\n");
                current.source.setLength(0);
            }
            if (!current.source.isEmpty()) current.source.append(' ');
            current.source.append(t);
            current.inSource = true;
            current.splitAt = current.statement.length();
            current.lines.add(line);
            return;
        }

        switch (mode) {
            case BASE -> {
                base.inSource = false;
                join(base.content, base.lastLine, line);
                base.lastLine = line;
            }
            case STATEMENT -> {
                current.inSource = false;
                appendToStatement(line);
                current.lines.add(line);
            }
            case ALTERNATIVES -> {
                if (alternativeEnded(line)) {
                    // A última alternativa já terminou e a leitura saltou de página/coluna: fim da questão.
                    finish();
                    return;
                }
                join(current.alternatives.getLast(), current.lastLine, line);
                current.lastLine = line;
                current.lines.add(line);
            }
            case NONE -> {
                // Capa, instruções gerais etc.: ignorados.
            }
        }
    }

    /** A alternativa atual terminou com pontuação e a próxima linha está em outra página, coluna ou bloco? */
    private boolean alternativeEnded(TextLine line) {
        String text = current.alternatives.getLast().toString().strip();
        TextLine last = current.lastLine;
        if (text.isEmpty() || last == null || !".;!?".contains(text.substring(text.length() - 1))) return false;
        boolean jump = line.page() != last.page()
                || line.y() < last.y() - 5
                || line.y() - last.bottom() > Math.max(last.fontSize(), 8) * 2.5;
        return jump;
    }

    private void acceptFigure(TextLine line) {
        if (mode == Mode.BASE && base != null) {
            base.draft.getFigures().add(region(line.figure()));
        } else if (current != null && (mode == Mode.STATEMENT || mode == Mode.ALTERNATIVES)) {
            current.figures.add(line);
            current.figurePositions.add(current.statement.length());
        }
    }

    /**
     * Linha de referência bibliográfica? Além dos padrões, uma linha logo após uma fonte, com o mesmo
     * corpo de letra (menor que o do texto), continua a referência.
     */
    private boolean isSource(TextLine line, String t, boolean inSource, TextLine previous) {
        if (SOURCE.matcher(t).matches()) return true;
        if (CITATION.matcher(t).matches() && t.length() < 200) return true;
        if ((SOURCE_ANYWHERE.matcher(t).matches() || CITATION_UPPER.matcher(t).matches())
                && t.length() < 220 && smallerThanBody(line, previous)) {
            return true;
        }
        if (!inSource) return false;
        if (SOURCE_CONTINUATION.matcher(t).matches() && t.length() < 160) return true;
        return sourceFont > 0 && Math.abs(line.fontSize() - sourceFont) < 0.3 && line.fontSize() < 9.5;
    }

    /** Referências costumam ter letra menor que a do texto. */
    private static boolean smallerThanBody(TextLine line, TextLine previous) {
        if (line.fontSize() <= 0 || line.fontSize() < 9.5) return true;
        return previous == null || previous.fontSize() <= 0 || line.fontSize() < previous.fontSize() - 0.5;
    }

    private void appendToStatement(TextLine line) {
        join(current.statement, current.lastLine, line);
        current.lastLine = line;
    }

    // ---------- Reconhecimento ----------

    /** Trata rótulos e instruções de texto de apoio. Retorna true se consumiu a linha. */
    private boolean tryBaseTextStart(TextLine line, String t) {
        boolean shared = FOR_QUESTIONS.matcher(t).matches()
                || RANGE.matcher(t).find() && TEXT_WORDS.matcher(t).find() && t.length() < 160;
        boolean label = TEXT_LABEL.matcher(t).matches() && t.length() <= 60;
        boolean read = READ_INSTRUCTION.matcher(t).matches();
        if (!shared && !label && !read) return false;

        boolean insideQuestion = current != null && current.alternatives.isEmpty() && mode == Mode.STATEMENT;
        if (!shared && insideQuestion && (labelStyle || read)) {
            // "TEXTO I" logo após "QUESTÃO 41": o texto pertence à própria questão.
            if (label) {
                if (!current.statement.isEmpty()) current.statement.append("\n\n");
                current.statement.append(t);
                current.lastLine = null;
                current.lines.add(line);
                return true;
            }
            return false;
        }

        finish();
        // "Leia o texto para responder às questões 1 a 3" seguido de "TEXTO I": mesmo texto-base.
        if (mode != Mode.BASE || base == null || base.hasContent()) base = new OpenBase();
        Matcher r = RANGE.matcher(t);
        if (r.find()) {
            base.from = Integer.parseInt(r.group(1));
            base.to = Integer.parseInt(r.group(2));
        }
        mode = Mode.BASE;
        return true;
    }

    private Matcher matchQuestionStart(TextLine line, String t) {
        Matcher m = QUESTION_LABEL.matcher(t);
        if (m.matches()) return m;
        if (labelStyle) return null;

        boolean canStartNumbered = current == null
                || !current.alternatives.isEmpty()
                || (!multipleChoiceSeen && mode == Mode.STATEMENT && current.statement.length() > 20);
        if (!canStartNumbered) return null;

        Integer open = current == null ? null : current.draft.getNumber();
        int previous = open != null ? open : lastNumber;
        for (Pattern p : new Pattern[]{NUMBERED, NUMBER_ONLY, NUMBER_SPACE}) {
            m = p.matcher(t);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                boolean sequential = previous == 0 ? n > 0 && n <= 300 : n == previous + 1;
                if (!sequential) continue;
                if (p == NUMBER_ONLY && !(line.bold() || mode != Mode.STATEMENT || current == null)) continue;
                // "1 Texto..." sem pontuação: na primeira questão, só se for o item 1 ou estiver em negrito.
                if (p == NUMBER_SPACE && previous == 0 && n != 1 && !line.bold()) continue;
                return m;
            }
        }
        return null;
    }

    private boolean tryAlternatives(TextLine line, String t) {
        char expected = (char) ('A' + current.alternatives.size());
        if (expected > 'E') return false;

        Character bare = bareAlternatives.get(line);
        if (bare != null) {
            if (bare != expected) return false;
            Matcher m = BARE_ALTERNATIVE.matcher(t);
            addAlternative(m.matches() && m.group(2) != null ? m.group(2).strip() : "", line);
            return true;
        }

        // Várias alternativas na mesma linha: "(A) 10 (B) 20 (C) 30".
        Matcher inline = INLINE_ALTERNATIVE.matcher(t);
        List<int[]> marks = new ArrayList<>();
        char want = expected;
        while (inline.find()) {
            if (inline.group(1).charAt(0) == want) {
                marks.add(new int[]{inline.start(), inline.end()});
                want++;
            }
        }
        if (marks.size() >= 2 && marks.getFirst()[0] == 0) {
            for (int i = 0; i < marks.size(); i++) {
                int end = i + 1 < marks.size() ? marks.get(i + 1)[0] : t.length();
                addAlternative(t.substring(marks.get(i)[1], end).strip(), line);
            }
            return true;
        }

        Matcher m = ALTERNATIVE.matcher(t);
        if (!m.matches()) return false;
        char letter = Character.toUpperCase(m.group(1).charAt(0));
        if (letter != expected) return false;
        // "a)" minúsculo só vale se o padrão minúsculo já começou com "a".
        if (Character.isLowerCase(m.group(1).charAt(0)) && expected != 'A' && !current.useLowercase) {
            return false;
        }
        if (Character.isLowerCase(m.group(1).charAt(0)) && expected == 'A') current.useLowercase = true;
        addAlternative(m.group(2).strip(), line);
        return true;
    }

    private void addAlternative(String text, TextLine line) {
        current.alternatives.add(new StringBuilder(text));
        current.altLines.add(line);
        current.lastLine = line;
        current.lines.add(line);
        current.inSource = false;
        mode = Mode.ALTERNATIVES;
        multipleChoiceSeen = true;
    }

    private boolean isSubjectHeading(TextLine line, String t) {
        if (t.length() < 4 || t.length() > 70 || t.matches(".*\\d.*") || t.endsWith(".") || t.endsWith(":")) {
            return false;
        }
        if (mode == Mode.STATEMENT && current != null && current.alternatives.isEmpty()) return false;
        // Dentro de texto-base, só títulos de área em negrito (ex.: "CIÊNCIAS HUMANAS E SUAS TECNOLOGIAS").
        if (mode == Mode.BASE && base != null && base.hasContent() && (base.from != null || !line.bold())) return false;
        long letters = t.chars().filter(Character::isLetter).count();
        long upper = t.chars().filter(Character::isUpperCase).count();
        boolean caps = letters > 0 && upper >= letters * 0.85;
        if (!caps && !line.bold()) return false;
        String n = stripAccents(t).toLowerCase(Locale.ROOT);
        for (String k : SUBJECT_KEYWORDS) {
            if (n.contains(k)) return true;
        }
        return false;
    }

    // ---------- Finalização ----------

    private void finish() {
        if (current == null) return;
        QuestionDraft d = current.draft;
        String body = current.statement.toString();

        boolean sharedApplies = false;
        if (base != null && base.hasContent()) {
            Integer n = d.getNumber();
            sharedApplies = base.from == null || n != null && n >= base.from && n <= base.to;
        }

        // Texto da própria questão: tudo antes da última fonte é texto de apoio; o resto é o comando.
        List<Region> baseFigures = new ArrayList<>();
        List<Region> statementFigures = new ArrayList<>();
        int split = current.splitAt;
        boolean localBase = !sharedApplies && split >= 0 && !body.substring(split).isBlank();
        for (int i = 0; i < current.figures.size(); i++) {
            Region r = region(current.figures.get(i).figure());
            if (localBase && current.figurePositions.get(i) <= split) baseFigures.add(r);
            else statementFigures.add(r);
        }
        if (localBase) {
            BaseTextDraft local = new BaseTextDraft(null, body.substring(0, split).strip(), current.source.toString().strip());
            local.getFigures().addAll(baseFigures);
            if (!local.isEmpty()) d.setBaseText(local);
            d.setStatement(body.substring(split).strip());
        } else {
            String text = body;
            if (!current.source.isEmpty() && split >= 0) {
                text = body.substring(0, split) + "\n" + current.source + "\n" + body.substring(split);
            }
            d.setStatement(text.strip());
        }

        for (int i = 0; i < current.alternatives.size(); i++) {
            d.addAlternative(String.valueOf((char) ('A' + i)), current.alternatives.get(i).toString().strip(), false);
        }
        assignAlternativeFigures(d, statementFigures);
        d.getFigures().addAll(statementFigures);

        if (current.alternatives.isEmpty() && !multipleChoiceSeen) {
            d.setTrueFalse(true);
            d.addAlternative("C", "Certo", false);
            d.addAlternative("E", "Errado", false);
        }

        if (sharedApplies) {
            base.draft.setContent(base.content.toString().strip());
            base.draft.setSource(base.source.isEmpty() ? null : base.source.toString().strip());
            d.setBaseText(base.draft);
        }

        score(d);
        d.setRegion(region(current.lines));
        d.setOcr(current.lines.stream().anyMatch(l -> l.confidence() < 100));
        result.add(d);
        if (d.getNumber() != null) lastNumber = d.getNumber();
        current = null;
        mode = Mode.NONE;
    }

    /**
     * Alternativas sem texto (ou com texto curto) recebem a figura ao lado da letra.
     * Se a geometria não resolver, as figuras posteriores à primeira alternativa são distribuídas em ordem.
     */
    private void assignAlternativeFigures(QuestionDraft d, List<Region> figures) {
        List<AlternativeDraft> alts = d.getAlternatives();
        if (alts.isEmpty() || figures.isEmpty()) return;
        for (int i = 0; i < alts.size(); i++) {
            AlternativeDraft a = alts.get(i);
            if (a.getText().length() > 3) continue;
            TextLine letter = current.altLines.get(i);
            double cy = letter.y() + letter.height() / 2;
            Region best = null;
            for (Region f : figures) {
                if (f.page() != letter.page()) continue;
                boolean vertical = cy >= f.y0() - 10 && cy <= f.y1() + 10;
                boolean right = f.x0() >= letter.x() - 5;
                if (vertical && right && (best == null || f.x0() < best.x0())) best = f;
            }
            if (best != null) {
                a.setFigure(best);
                figures.remove(best);
            }
        }
        List<AlternativeDraft> empty = alts.stream().filter(a -> a.getText().isBlank() && a.getFigure() == null).toList();
        if (empty.isEmpty()) return;
        TextLine first = current.altLines.getFirst();
        List<Region> after = figures.stream()
                .filter(f -> f.page() > first.page() || f.page() == first.page() && f.y1() >= first.y() - 10)
                .toList();
        if (after.size() == empty.size()) {
            for (int i = 0; i < empty.size(); i++) {
                empty.get(i).setFigure(after.get(i));
                figures.remove(after.get(i));
            }
        }
    }

    private void score(QuestionDraft d) {
        double c = 1.0;
        int alts = d.isTrueFalse() ? 2 : d.getAlternatives().size();
        if (!d.isTrueFalse() && alts < 4) {
            c -= alts < 2 ? 0.5 : 0.25;
            d.getWarnings().add("Foram encontradas " + alts + " alternativas.");
        }
        long empty = d.getAlternatives().stream().filter(a -> !a.hasContent()).count();
        if (empty > 0) {
            c -= 0.3;
            d.getWarnings().add(empty + " alternativa(s) sem texto nem imagem.");
        }
        boolean hasBase = d.getBaseText() != null && !d.getBaseText().isEmpty();
        if (d.getStatement().length() < 15 && d.getFigures().isEmpty() && !hasBase) {
            c -= 0.3;
            d.getWarnings().add("Enunciado muito curto ou ausente.");
        }
        if (lastNumber > 0 && d.getNumber() != null && d.getNumber() != lastNumber + 1) {
            c -= 0.1;
            d.getWarnings().add("Numeração fora de sequência (anterior: " + lastNumber + ").");
        }
        double avgConf = current.lines.stream().mapToDouble(TextLine::confidence).average().orElse(100);
        if (avgConf < 100) {
            c *= 0.5 + 0.5 * avgConf / 100;
            if (avgConf < 75) d.getWarnings().add(String.format("Leitura por OCR com confiança baixa (%.0f%%).", avgConf));
        }
        boolean hasFigure = !d.getFigures().isEmpty()
                || d.getAlternatives().stream().anyMatch(a -> a.getFigure() != null)
                || d.getBaseText() != null && !d.getBaseText().getFigures().isEmpty();
        String all = d.getStatement() + (d.getBaseText() == null || d.getBaseText().getContent() == null
                ? "" : " " + d.getBaseText().getContent());
        if (!hasFigure && IMAGE_HINT.matcher(all).find()) {
            d.getWarnings().add("Pode depender de uma imagem: considere anexar o recorte da página.");
        }
        d.setConfidence(c);
    }

    private static Region region(ImageBox b) {
        return new Region(b.page(), b.x(), b.y(), b.right(), b.bottom());
    }

    /** Área da questão na primeira página em que aparece, limitada à coluna do início. */
    private static Region region(List<TextLine> lines) {
        if (lines.isEmpty()) return null;
        TextLine first = lines.getFirst();
        double x0 = first.x(), x1 = first.right(), y0 = first.y(), y1 = first.bottom();
        for (TextLine l : lines) {
            if (l.page() != first.page()) continue;
            boolean sameColumn = l.x() < first.x() + Math.max(first.width(), 200) && l.right() > first.x() - 20;
            if (!sameColumn || l.y() < first.y() - 5) continue;
            x0 = Math.min(x0, l.x());
            x1 = Math.max(x1, l.right());
            y1 = Math.max(y1, l.bottom());
        }
        return new Region(first.page(), x0, y0, x1, y1);
    }

    // ---------- Texto ----------

    /** Junta uma linha ao texto, tratando hifenização e quebras de parágrafo. */
    static void join(StringBuilder sb, TextLine prev, TextLine line) {
        String t = line.text().strip();
        if (sb.isEmpty()) {
            sb.append(t);
            return;
        }
        boolean paragraph = prev != null && prev.page() == line.page()
                && Math.abs(prev.x() - line.x()) < 250
                && line.bottom() - prev.bottom() > Math.max(prev.fontSize(), 6) * 1.75;
        int last = sb.length() - 1;
        if (!paragraph && sb.charAt(last) == '-' && last > 0 && Character.isLetter(sb.charAt(last - 1))
                && !t.isEmpty() && Character.isLowerCase(t.charAt(0))) {
            sb.setLength(last);
            sb.append(t);
        } else {
            sb.append(paragraph ? "\n" : " ").append(t);
        }
    }

    private static String stripAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static String toTitleCase(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (String w : lower.split("\\s+")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            boolean small = w.length() <= 2 || w.equals("das") || w.equals("dos") || w.equals("suas");
            sb.append(small && !sb.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return sb.toString();
    }
}
