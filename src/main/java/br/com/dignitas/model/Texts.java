package br.com.dignitas.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Utilidades de texto: normalização, hash e abreviação. */
public final class Texts {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private Texts() {
    }

    /** Minúsculas, sem acentos, sem pontuação nem espaços: usado para detectar duplicidade. */
    public static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = DIACRITICS.matcher(n).replaceAll("").toLowerCase();
        return NON_ALNUM.matcher(n).replaceAll("");
    }

    public static String sha256(String s) {
        return sha256(s.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static String blankToNull(String s) {
        return isBlank(s) ? null : s.strip();
    }

    /** Abrevia em uma linha, para tabelas e listas. */
    public static String abbreviate(String s, int max) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() <= max ? one : one.substring(0, Math.max(0, max - 1)).stripTrailing() + "…";
    }
}
