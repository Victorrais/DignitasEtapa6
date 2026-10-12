# Etapa 6 — Refatoração do Dignitas para reutilização

Projeto NetBeans (Maven) em `dignitas-core/`, derivado do Dignitas JavaFX (branch `rewrite/remove-legacy`).
O objetivo foi separar as regras de negócio da interface gráfica, de modo que as classes possam ser
reaproveitadas num sistema web. O pacote `ui` e a classe `app` (configuração e inicialização JavaFX) ficaram
fora deste projeto, por não pertencerem às regras de negócio.

## 1. Estrutura resultante

| Pacote | Conteúdo | Depende de UI? |
|---|---|---|
| `domain` | Entidades JPA (`Question`, `Alternative`, `Exam`, `Subject`, `BaseText`, `Attempt`, `ImportBatch`) | Não |
| `model` | Rascunhos e utilitários (`QuestionDraft`, `BaseTextDraft`, `ExamInfo`, `ExamLabels`, `Texts`) | Não |
| `persistence` | `Jpa` (EntityManagerFactory) e `SchemaInstaller` (executa `schema.sql`) | Não |
| `service` | Casos de uso: consultas, comandos, importação, estatísticas, DTOs | Não |
| `extraction` | Leitura de PDF (PDFBox), OCR (Tess4J) e segmentação de questões | Não |
| raiz | `SelfTest` — verificação executada pelo `main()` | Não |

Verificação de dependências: nenhuma classe dos pacotes acima importa `javafx`, `javax.swing` ou `app.AppConfig`.

## 2. Princípios SOLID aplicados

### Responsabilidade Única (SRP) — principal princípio

`QuestionService` (301 linhas) acumulava consultas paginadas, montagem de filtros SQL, conversão
entidade↔rascunho, criação de disciplinas e concursos, limpeza de registros órfãos e os comandos de escrita.
Foi dividido em cinco classes:

| Classe nova | Responsabilidade |
|---|---|
| `QuestionQueries` | Somente leituras sobre questões (busca paginada, ids, carga completa, contagem, hashes existentes) |
| `QuestionMapper` | Conversão entre `Question` e `QuestionDraft` nos dois sentidos |
| `CatalogService` | Disciplinas e concursos: criação sem duplicar e listas para filtros |
| `OrphanCleaner` | Remoção de textos-base e concursos que ficaram sem uso |
| `QuestionService` | Comandos: `save`, `delete`, `setFlagged` (mantém as leituras como fachada) |

`ImportService` e `AttemptService` passaram a usar apenas a classe de que precisam.

### Inversão de Dependência (DIP)

- `Jpa.init(AppConfig)` dependia da classe de configuração da aplicação. Agora `Jpa.init(url, user, password)`
  recebe os dados como parâmetros; o chamador (a interface web, ou o `SelfTest`) decide a origem.
- `TessdataManager` lia o diretório de `AppConfig.get()` em métodos estáticos. Agora é instanciado com o
  `Path` do diretório (`new TessdataManager(dir)`), o que o torna testável sem configuração global.

### Aberto/Fechado (OCP)

- `QuestionQueries.where()` monta a cláusula a partir do filtro. Novos critérios são acrescentados num ponto
  único, sem alterar a montagem do restante da consulta.

### Segregação de Interface (ISP)

- Cada classe pública expõe apenas os métodos do seu papel. Quem só lê questões (por exemplo, a tela de
  estatísticas ou uma API de consulta) depende de `QuestionQueries`, sem acesso aos comandos de escrita.

## 3. Refatorações aplicadas

| # | Refatoração | Onde | Motivo (code smell) |
|---|---|---|---|
| 1 | **Extract Class** | `QuestionService` → `QuestionQueries`, `QuestionMapper`, `CatalogService`, `OrphanCleaner` | Classe com muitas responsabilidades (*Large Class*) |
| 2 | **Move Method** | `toDraft`, `apply` → `QuestionMapper`; `findOrCreateSubject/Exam` → `CatalogService`; `deleteOrphans` → `OrphanCleaner` | Métodos estáticos de pacote usados por outra classe (*Feature Envy*/acoplamento) |
| 3 | **Extract Method** | `QuestionMapper.apply` → `resolveBaseText`, `applyAlternatives` | Método longo com três assuntos (*Long Method*) |
| 4 | **Remove Duplication** | Filtro de texto `lower(q.statement) like :text` duplicado em `QuestionService.where` e `AttemptService.history` → `TextFilters` | Código duplicado (*Duplicated Code*) |
| 5 | **Introduce Parameter / Remove Global State** | `Jpa.init(AppConfig)` → `Jpa.init(url, user, password)`; `TessdataManager` estático → instância com `Path` | Dependência de configuração global (*Global Data*) |
| 6 | **Move Class** | `Dtos.ExamLabels` → `model.ExamLabels` | Formatação de apresentação dentro da camada de DTOs (*Misplaced Code*) |
| 7 | **Remove Unused Import** | `AttemptService` (`Texts`) | Código morto |
| 8 | **Encapsulate Field / Visibility** | Métodos de montagem de `QuestionQueries.where` e `Where` são `private`; `QuestionMapper` com construtor privado | Exposição desnecessária |

Comportamento preservado: as 23 verificações do `SelfTest` cobrem os fluxos originais (ver seção 4).

## 4. Testes no `main()`

`br.com.dignitas.SelfTest.main()` cria um banco H2 em memória no modo MySQL, executa o `schema.sql` real e
verifica:

- inserção de dados de exemplo e detecção de duplicidade;
- carga completa da questão (alternativas, texto-base, disciplina, concurso);
- **ida e volta** entidade → rascunho → hash (regressão do `QuestionMapper`);
- recusa de questão duplicada e de rascunho inválido;
- registro de respostas, histórico e estatísticas;
- filtros por status (não respondidas, já errei);
- importação com bloqueio de arquivo repetido;
- remoção de concurso órfão ao excluir a última questão (regressão do `OrphanCleaner`).

Resultado da última execução: **23 verificações aprovadas, 0 falharam.**

Execução:

```bash
./mvnw -q -B compile
```

```bash
java -Dstdout.encoding=UTF-8 -cp "target/classes;<classpath>" br.com.dignitas.SelfTest
```

O classpath pode ser gerado com `./mvnw dependency:build-classpath -Dmdep.outputFile=cp.txt`.

## 5. Pendências conhecidas (não tratadas nesta etapa)

- `QuestionSegmenter` (724 linhas) continua sendo a maior classe e mistura leitura de layout, detecção de
  questões e montagem de alternativas. Deve ser dividida em etapas (layout, segmentação, montagem).
- `QuestionDraft` (269 linhas) mistura dados editáveis, metadados da extração (região, figuras, avisos) e
  validação.
- `Dtos` ainda contém enums com rótulos em português (`Status`, `Sort`, `ResultFilter`). Esses rótulos são de
  apresentação; o ideal é movê-los para a camada web.
- `Jpa` mantém um `EntityManagerFactory` estático. Funciona para uma aplicação, mas dificulta testes paralelos.
- Os testes de extração da versão JavaFX (`ExtractionTest`, `ImportReport`) dependem de PDFs de exemplo e de OCR
  instalado; não foram portados.

## 6. Evidências do repositório

- Repositório GitHub: **pendente** (criação local apenas, conforme solicitado).
- Histórico local: `git log --oneline` no diretório `netbeans-dignitas/`.
- Projeto de origem: branch `rewrite/remove-legacy` do repositório `Dignitas`, commit `6ae5448`.
