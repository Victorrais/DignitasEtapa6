-- Dignitas: esquema do banco (MySQL 8+).
-- Pode ser executado várias vezes: só cria o que ainda não existe.
-- O banco em si é criado pela URL JDBC (createDatabaseIfNotExist=true) ou manualmente:
--   CREATE DATABASE IF NOT EXISTS dignitasdb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS exam (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    board      VARCHAR(120),
    exam_year  INT,
    role       VARCHAR(255)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS subject (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(120) NOT NULL,
    CONSTRAINT uk_subject_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS import_batch (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    exam_id      BIGINT,
    file_name    VARCHAR(255) NOT NULL,
    file_hash    CHAR(64) NOT NULL,
    page_count   INT NOT NULL,
    ocr_pages    INT NOT NULL DEFAULT 0,
    imported_at  DATETIME NOT NULL,
    CONSTRAINT uk_import_file_hash UNIQUE (file_hash),
    CONSTRAINT fk_import_exam FOREIGN KEY (exam_id) REFERENCES exam (id) ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS base_text (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    content  MEDIUMTEXT NOT NULL,
    source   VARCHAR(1000),
    image    LONGBLOB
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS question (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    import_id     BIGINT,
    exam_id       BIGINT,
    subject_id    BIGINT,
    base_text_id  BIGINT,
    number        INT,
    statement     TEXT NOT NULL,
    content_hash  CHAR(64) NOT NULL,
    annulled      BOOLEAN NOT NULL DEFAULT FALSE,
    flagged       BOOLEAN NOT NULL DEFAULT FALSE,
    image         LONGBLOB,
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    INDEX idx_question_updated_at (updated_at),
    CONSTRAINT uk_question_content_hash UNIQUE (content_hash),
    CONSTRAINT fk_question_import FOREIGN KEY (import_id) REFERENCES import_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_question_exam FOREIGN KEY (exam_id) REFERENCES exam (id) ON DELETE SET NULL,
    CONSTRAINT fk_question_subject FOREIGN KEY (subject_id) REFERENCES subject (id) ON DELETE SET NULL,
    CONSTRAINT fk_question_base_text FOREIGN KEY (base_text_id) REFERENCES base_text (id) ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS alternative (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id  BIGINT NOT NULL,
    letter       CHAR(1) NOT NULL,
    statement    TEXT NOT NULL,
    is_answer    BOOLEAN NOT NULL DEFAULT FALSE,
    image        LONGBLOB,
    CONSTRAINT fk_alternative_question FOREIGN KEY (question_id) REFERENCES question (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS attempt (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id     BIGINT NOT NULL,
    alternative_id  BIGINT,
    chosen_letter   CHAR(1),
    correct         BOOLEAN NOT NULL,
    answered_at     DATETIME NOT NULL,
    INDEX idx_attempt_answered_at (answered_at),
    CONSTRAINT fk_attempt_question FOREIGN KEY (question_id) REFERENCES question (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_alternative FOREIGN KEY (alternative_id) REFERENCES alternative (id) ON DELETE SET NULL
) ENGINE = InnoDB;
