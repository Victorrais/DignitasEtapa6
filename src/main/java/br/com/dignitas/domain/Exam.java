package br.com.dignitas.domain;

import jakarta.persistence.*;

/** Concurso/prova de origem das questões (ex.: "TJ-SP 2024 – Escrevente", banca VUNESP). */
@Entity
@Table(name = "exam")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 120)
    private String board;

    @Column(name = "exam_year")
    private Integer year;

    @Column(length = 255)
    private String role;

    public Exam() {
    }

    public Exam(String name, String board, Integer year, String role) {
        this.name = name;
        this.board = board;
        this.year = year;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    /** Texto curto para exibição: "VUNESP · 2024 · TJ-SP". */
    public String label() {
        StringBuilder sb = new StringBuilder();
        if (board != null && !board.isBlank()) sb.append(board);
        if (year != null) sb.append(sb.isEmpty() ? "" : " · ").append(year);
        if (name != null && !name.isBlank()) sb.append(sb.isEmpty() ? "" : " · ").append(name);
        return sb.toString();
    }

    @Override
    public String toString() {
        return label();
    }
}
