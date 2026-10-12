package br.com.dignitas.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Executa {@code resources/sql/schema.sql} via JDBC. O script é idempotente. */
public final class SchemaInstaller {
    private SchemaInstaller() {
    }

    public static void install(String url, String user, String password) throws SQLException, IOException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            install(c);
        }
    }

    public static void install(Connection connection) throws SQLException, IOException {
        try (Statement st = connection.createStatement()) {
            for (String sql : statements()) {
                st.execute(sql);
            }
            migrate(connection, st);
        }
    }

    /** Colunas acrescentadas depois da primeira versão: bancos já criados recebem-nas aqui. */
    private static void migrate(Connection c, Statement st) throws SQLException {
        addColumnIfMissing(c, st, "alternative", "image", "LONGBLOB");
        addColumnIfMissing(c, st, "base_text", "image", "LONGBLOB");
    }

    private static void addColumnIfMissing(Connection c, Statement st, String table, String column, String type)
            throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        for (String t : new String[]{table, table.toUpperCase()}) {
            try (ResultSet rs = md.getColumns(c.getCatalog(), null, t, null)) {
                boolean tableFound = false;
                while (rs.next()) {
                    tableFound = true;
                    if (rs.getString("COLUMN_NAME").equalsIgnoreCase(column)) return;
                }
                if (tableFound) {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                    return;
                }
            }
        }
    }

    /** Apenas abre e fecha uma conexão; lança a exceção do driver em caso de falha. */
    public static void testConnection(String url, String user, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.isValid(3);
        }
    }

    static List<String> statements() throws IOException {
        String script;
        try (InputStream in = SchemaInstaller.class.getResourceAsStream("/sql/schema.sql")) {
            if (in == null) throw new IOException("schema.sql não encontrado no classpath");
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().strip();
                result.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) result.add(current.toString().strip());
        return result;
    }
}
