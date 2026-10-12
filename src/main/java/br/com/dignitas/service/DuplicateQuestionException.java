package br.com.dignitas.service;

public class DuplicateQuestionException extends RuntimeException {
    public DuplicateQuestionException() {
        super("Já existe uma questão com o mesmo enunciado e alternativas no banco.");
    }
}
