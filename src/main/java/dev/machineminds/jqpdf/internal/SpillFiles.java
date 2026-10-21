package dev.machineminds.jqpdf.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-run spill-file registry for in-memory inputs (spec §9.1): materializes
 * caller-supplied bytes into files qpdf can open, in a configurable
 * directory, and deletes exactly the files this run created.
 *
 * <p>Directory resolution happens when the first file is spilled and follows:
 * the job's {@code tempDir(...)} override, the {@code jqpdf.tmpdir} system
 * property, the {@code JQPDF_TMPDIR} environment variable, and finally
 * {@code java.io.tmpdir}. A configured directory that is missing, not a
 * directory, or not writable fails fast with {@link IllegalStateException}
 * naming the knob — never a silent fallback.
 *
 * <p>Files are created exclusively through {@link Files#createTempFile},
 * whose create-new semantics guarantee that concurrent jobs (or separate
 * processes sharing the directory) never receive the same path.
 */
public final class SpillFiles {

    private static final String TEMP_DIR_PROPERTY = "jqpdf.tmpdir";
    private static final String TEMP_DIR_ENV = "JQPDF_TMPDIR";

    private final Path jobTempDir;
    private final String configuredProperty;
    private final String configuredEnv;
    private final List<Path> created = new ArrayList<>();

    /** Production factory: reads the configured knobs at run time. */
    public static SpillFiles forJob(Path jobTempDir) {
        return new SpillFiles(
                jobTempDir, System.getProperty(TEMP_DIR_PROPERTY), System.getenv(TEMP_DIR_ENV));
    }

    /** Test seam: knob values supplied directly, so the chain is observable. */
    SpillFiles(Path jobTempDir, String configuredProperty, String configuredEnv) {
        this.jobTempDir = jobTempDir;
        this.configuredProperty = configuredProperty;
        this.configuredEnv = configuredEnv;
    }

    /** Spills {@code content} as {@code jqpdf-*<suffix>} into the resolved directory. */
    public Path spill(byte[] content, String suffix) {
        Path file = newSpill(suffix);
        try {
            Files.write(file, content);
        } catch (IOException e) {
            deleteQuietly(file);
            throw new UncheckedIOException("writing spill file " + file + " failed", e);
        }
        return file;
    }

    /** Streams {@code in} into a fresh {@code jqpdf-*<suffix>} file; never buffers it fully. */
    public Path spill(InputStream in, String suffix) {
        Path file = newSpill(suffix);
        try (OutputStream out = Files.newOutputStream(file)) {
            in.transferTo(out);
        } catch (IOException e) {
            deleteQuietly(file);
            throw new UncheckedIOException("writing spill file " + file + " failed", e);
        }
        return file;
    }

    /**
     * Best-effort deletion of the files this run created; a file that cannot
     * be removed is abandoned (never throws, never touches other files).
     */
    public void cleanup() {
        for (Path file : created) {
            deleteQuietly(file);
        }
        created.clear();
    }

    private Path newSpill(String suffix) {
        Path dir = resolveDir();
        try {
            Path file = Files.createTempFile(dir, "jqpdf-", suffix);
            created.add(file);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("creating spill file in " + dir + " failed", e);
        }
    }

    private Path resolveDir() {
        if (jobTempDir != null) {
            return validated(jobTempDir, "tempDir(...)");
        }
        if (configuredProperty != null && !configuredProperty.isBlank()) {
            return validated(Paths.get(configuredProperty), "system property " + TEMP_DIR_PROPERTY);
        }
        if (configuredEnv != null && !configuredEnv.isBlank()) {
            return validated(Paths.get(configuredEnv), "environment variable " + TEMP_DIR_ENV);
        }
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    private static Path validated(Path dir, String knob) {
        if (!Files.isDirectory(dir) || !Files.isWritable(dir)) {
            throw new IllegalStateException(
                    "jqpdf temp directory " + dir + " (from " + knob + ") is not a writable directory");
        }
        return dir;
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // best effort: leftover files are documented in the memory-io guide
        }
    }
}
