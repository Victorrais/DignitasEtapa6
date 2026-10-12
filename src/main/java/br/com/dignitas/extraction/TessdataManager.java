package br.com.dignitas.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.DoubleConsumer;

/**
 * Localiza e baixa o modelo de idioma português do Tesseract.
 * Recebe o diretório por construtor: não conhece configuração de aplicação nem telas.
 */
public final class TessdataManager {
    public static final String LANGUAGE = "por";
    public static final String DOWNLOAD_URL =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/" + LANGUAGE + ".traineddata";
    /** Abaixo disso o arquivo é considerado corrompido ou incompleto. */
    private static final long MIN_MODEL_SIZE = 100_000;

    private final Path dir;

    public TessdataManager(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    public Path modelFile() {
        return dir.resolve(LANGUAGE + ".traineddata");
    }

    public boolean isInstalled() {
        try {
            return Files.isRegularFile(modelFile()) && Files.size(modelFile()) > MIN_MODEL_SIZE;
        } catch (IOException e) {
            return false;
        }
    }

    /** Baixa o modelo (uma única vez). {@code progress} recebe valores de 0 a 1, ou -1 se o tamanho for desconhecido. */
    public void download(DoubleConsumer progress) throws IOException, InterruptedException {
        Files.createDirectories(dir);
        Path part = dir.resolve(LANGUAGE + ".traineddata.part");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(DOWNLOAD_URL)).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Falha no download (HTTP " + response.statusCode() + ")");
        }
        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                done += n;
                progress.accept(total > 0 ? (double) done / total : -1);
            }
        }
        Files.move(part, modelFile(), StandardCopyOption.REPLACE_EXISTING);
    }
}
