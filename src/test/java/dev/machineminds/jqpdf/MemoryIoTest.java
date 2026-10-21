package dev.machineminds.jqpdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** In-memory inputs and outputs (spec §9.1): argv mapping, spill lifecycle, guards. */
class MemoryIoTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7\nfake\n%%EOF\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void inputBytesSpillToPdfTempFileRemovedAfterRun() {
        FakeQpdfNative fake = new FakeQpdfNative();
        List<byte[]> spilledAtInit = new ArrayList<>();
        fake.onInitialize = spilledBytesSink(spilledAtInit);

        JobResult result = Jqpdf.createJob(fake).input(PDF_BYTES).outputMemory().run();

        assertEquals(0, result.exitCode());
        assertSpilledOnce(spilledAtInit, PDF_BYTES);
        Path infile = Path.of(fake.argvOfRun(0).get(0));
        assertTrue(isSpillFile(infile, ".pdf"), "spilled input should be a jqpdf-*.pdf temp file, got: " + infile);
        assertFalse(Files.exists(infile), "spilled input must be deleted after the run");
        assertEquals("-", last(fake.argvOfRun(0)), "outputMemory() maps to CLI outfile '-'");
    }

    @Test
    void inputStreamSpillsStreamedContentRemovedAfterRun() {
        FakeQpdfNative fake = new FakeQpdfNative();
        List<byte[]> spilledAtInit = new ArrayList<>();
        fake.onInitialize = spilledBytesSink(spilledAtInit);

        Jqpdf.createJob(fake).input(new ByteArrayInputStream(PDF_BYTES)).output("out.pdf").run();

        assertSpilledOnce(spilledAtInit, PDF_BYTES);
        Path infile = Path.of(fake.argvOfRun(0).get(0));
        assertTrue(isSpillFile(infile, ".pdf"), "streamed input should be a jqpdf-*.pdf temp file, got: " + infile);
        assertFalse(Files.exists(infile), "spilled stream input must be deleted after the run");
    }

    @Test
    void inputPathPassesThroughWithoutSpilling() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake).input(Path.of("docs", "sample.pdf")).output("out.pdf").run();

        String infile = fake.argvOfRun(0).get(0);
        assertEquals(Path.of("docs", "sample.pdf").toString(), infile);
        assertFalse(Path.of(infile).getFileName().toString().startsWith("jqpdf-"),
                "input(Path) must be passed through, not spilled");
    }

    @Test
    void outputCallsOverrideEachOtherLastOneWins() {
        FakeQpdfNative toMemory = new FakeQpdfNative();
        Jqpdf.createJob(toMemory).input("in.pdf").output("a.pdf").outputMemory().run();
        assertEquals("-", last(toMemory.argvOfRun(0)));

        FakeQpdfNative toFile = new FakeQpdfNative();
        Jqpdf.createJob(toFile).input("in.pdf").outputMemory().output("b.pdf").run();
        assertEquals("b.pdf", last(toFile.argvOfRun(0)));
    }

    @Test
    void replaceInputRejectedForInMemoryMainInput() {
        assertThrows(QpdfUsageException.class,
                () -> Jqpdf.createJob(new FakeQpdfNative()).input(PDF_BYTES).replaceInput().run());
        assertThrows(QpdfUsageException.class, () -> Jqpdf.createJob(new FakeQpdfNative())
                .input(new ByteArrayInputStream(PDF_BYTES)).replaceInput().run());
        assertThrows(QpdfUsageException.class, () -> Jqpdf.createJob(new FakeQpdfNative())
                .input("in.pdf").jsonInputContent("{}").replaceInput().run());
    }

    @Test
    void replaceInputAllowedForPathInput() {
        FakeQpdfNative fake = new FakeQpdfNative();

        JobResult result = Jqpdf.createJob(fake).input(Path.of("in.pdf")).replaceInput().run();

        assertEquals(0, result.exitCode());
        assertTrue(fake.argvOfRun(0).contains("--replace-input"));
    }

    @Test
    void savedTextDecodesSavedDataAsUtf8() {
        FakeQpdfNative fake = new FakeQpdfNative();
        fake.savedData = "{\"a\": \"ünïcode\"}".getBytes(StandardCharsets.UTF_8);

        JobResult result = Jqpdf.createJob(fake).input("in.pdf").json().run();

        assertEquals("{\"a\": \"ünïcode\"}", result.savedText());
        assertArrayEquals(fake.savedData, result.savedData());
    }

    @Test
    void jsonInputContentSpillsJsonDocumentAsTheInput() {
        String json = "{\"qpdf\": [1, 2]}";
        FakeQpdfNative fake = new FakeQpdfNative();
        List<byte[]> spilledAtInit = new ArrayList<>();
        fake.onInitialize = spilledBytesSink(spilledAtInit);

        Jqpdf.createJob(fake).jsonInputContent(json).output("out.pdf").run();

        assertSpilledOnce(spilledAtInit, json.getBytes(StandardCharsets.UTF_8));
        List<String> argv = fake.argvOfRun(0);
        assertTrue(isSpillFile(Path.of(argv.get(0)), ".json"),
                "json input should be spilled as jqpdf-*.json, got: " + argv.get(0));
        assertEquals("--json-input", argv.get(1));
        assertFalse(Files.exists(Path.of(argv.get(0))), "spilled JSON input must be deleted after the run");
    }

    @Test
    void jsonInputContentBytesBehaveLikeTheStringForm() {
        byte[] json = "{\"qpdf\": []}".getBytes(StandardCharsets.UTF_8);
        FakeQpdfNative fake = new FakeQpdfNative();
        List<byte[]> spilledAtInit = new ArrayList<>();
        fake.onInitialize = spilledBytesSink(spilledAtInit);

        Jqpdf.createJob(fake).jsonInputContent(json).output("out.pdf").run();

        assertSpilledOnce(spilledAtInit, json);
        assertTrue(fake.argvOfRun(0).contains("--json-input"));
    }

    @Test
    void updateFromJsonContentSpillsDocumentForTheOption() {
        String json = "{\"objects\": {}}";
        FakeQpdfNative fake = new FakeQpdfNative();
        List<byte[]> spilledAtInit = new ArrayList<>();
        fake.onInitialize = spilledBytesSink(spilledAtInit);

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf").updateFromJsonContent(json).run();

        Path spilled = spilledOptionPath(fake, "--update-from-json=");
        assertSpilledOnce(spilledAtInit, json.getBytes(StandardCharsets.UTF_8));
        assertTrue(isSpillFile(spilled, ".json"),
                "update JSON should be spilled as jqpdf-*.json, got: " + spilled);
        assertFalse(Files.exists(spilled), "spilled update JSON must be deleted after the run");
    }

    @Test
    void updateFromJsonContentBytesBehaveLikeTheStringForm() {
        byte[] json = "{\"objects\": {}}".getBytes(StandardCharsets.UTF_8);
        FakeQpdfNative fake = new FakeQpdfNative();
        List<byte[]> spilledAtInit = new ArrayList<>();
        fake.onInitialize = spilledBytesSink(spilledAtInit);

        Jqpdf.createJob(fake).input("in.pdf").output("out.pdf").updateFromJsonContent(json).run();

        assertSpilledOnce(spilledAtInit, json);
        assertTrue(Files.exists(spilledOptionPath(fake, "--update-from-json=").getParent()));
    }

    @Test
    void attachmentContentValidationRejectsMissingKeyFileAndBoth() {
        FakeQpdfNative fake = new FakeQpdfNative();
        QpdfJob job = Jqpdf.createJob(fake).input("in.pdf").output("out.pdf");

        assertThrows(QpdfUsageException.class,
                () -> job.addAttachment(a -> a.content(new byte[] {1})),
                "content without key must be rejected at configuration time");
        assertThrows(QpdfUsageException.class,
                () -> job.addAttachment(a -> a.file("note.txt").content(new byte[] {1}).key("k")),
                "file together with content must be rejected");
        assertThrows(QpdfUsageException.class,
                () -> job.addAttachment(a -> a.key("k")),
                "a group with neither file nor content must be rejected");
        assertEquals(0, fake.runCount(), "validation failures must not start a run");
    }

    @Test
    void attachmentContentSpillsPayloadRemovedAfterRun() {
        byte[] payload = {1, 2, 3, 4};
        FakeQpdfNative fake = new FakeQpdfNative();
        List<byte[]> spilledAtInit = new ArrayList<>();
        fake.onInitialize = spilledBytesSink(spilledAtInit);

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .addAttachment(a -> a.content(payload).key("data.bin"))
                .run();

        assertSpilledOnce(spilledAtInit, payload);
        List<String> argv = fake.argvOfRun(0);
        Path spilled = Path.of(argv.get(argv.indexOf("--add-attachment") + 1));
        assertEquals("--key=data.bin", argv.get(argv.indexOf(spilled.toString()) + 1));
        assertTrue(spilled.getFileName().toString().endsWith(".bin"));
        assertFalse(Files.exists(spilled), "spilled attachment payload must be deleted after the run");
    }

    @Test
    void concurrentJobsSpillToDistinctFilesAndCleanUp() throws Exception {
        int jobCount = 24;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Path>> tasks = new ArrayList<>();
            for (int i = 0; i < jobCount; i++) {
                byte[] content = ("run-" + i).getBytes(StandardCharsets.UTF_8);
                tasks.add(() -> {
                    FakeQpdfNative fake = new FakeQpdfNative();
                    List<Path> spilledAtInit = new ArrayList<>();
                    fake.onInitialize = argv -> {
                        Path infile = Path.of(argv.get(1));
                        assertTrue(Files.exists(infile), "spill must exist at initialize time: " + infile);
                        spilledAtInit.add(infile);
                    };
                    JobResult result = Jqpdf.createJob(fake).input(content).outputMemory().run();
                    assertEquals(0, result.exitCode());
                    assertEquals(1, spilledAtInit.size());
                    return spilledAtInit.get(0);
                });
            }
            List<Future<Path>> futures = pool.invokeAll(tasks);

            Set<Path> paths = new HashSet<>();
            for (Future<Path> future : futures) {
                Path infile = future.get();
                assertFalse(Files.exists(infile), "spill must be removed after its run: " + infile);
                assertTrue(paths.add(infile), "spill paths must be distinct across jobs: " + infile);
            }
            assertEquals(jobCount, paths.size());
        } finally {
            pool.shutdownNow();
        }
    }

    private static boolean isSpillFile(Path file, String suffix) {
        String name = file.getFileName().toString();
        return name.startsWith("jqpdf-") && name.endsWith(suffix);
    }

    private static void assertSpilledOnce(List<byte[]> spilled, byte[] expected) {
        assertEquals(1, spilled.size(), "exactly one spill file expected");
        assertArrayEquals(expected, spilled.get(0));
    }

    /**
     * Captures the bytes of every {@code jqpdf-*} path in argv (bare tokens
     * like the infile, or {@code --option=<path>} values) at initialize time.
     */    private static Consumer<List<String>> spilledBytesSink(List<byte[]> sink) {
        return argv -> {
            for (String token : argv) {
                String value = token.startsWith("--") && token.contains("=")
                        ? token.substring(token.indexOf('=') + 1)
                        : token;
                String name = Path.of(value).getFileName().toString();
                if (name.startsWith("jqpdf-")) {
                    try {
                        sink.add(Files.readAllBytes(Path.of(value)));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        };
    }

    private static Path spilledOptionPath(FakeQpdfNative fake, String prefix) {
        return fake.argvOfRun(0).stream()
                .filter(token -> token.startsWith(prefix))
                .map(token -> Path.of(token.substring(prefix.length())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + prefix + " token in argv"));
    }

    private static String last(List<String> argv) {
        return argv.get(argv.size() - 1);
    }
}
