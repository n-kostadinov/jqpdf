package dev.machineminds.jqpdf.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Temp-directory resolution chain, fail-fast validation, spill lifecycle (spec §9.1). */
class SpillFilesTest {

    @Test
    void resolvesJobDirThenPropertyThenEnvThenJvmDefault(@TempDir Path temp) throws IOException {
        Path jobDir = directory(temp, "job");
        Path propertyDir = directory(temp, "property");
        Path envDir = directory(temp, "env");

        assertEquals(jobDir, spillParent(new SpillFiles(jobDir, propertyDir.toString(), envDir.toString())));
        assertEquals(propertyDir, spillParent(new SpillFiles(null, propertyDir.toString(), envDir.toString())));
        assertEquals(envDir, spillParent(new SpillFiles(null, "  ", envDir.toString())),
                "a blank property value must fall through to the environment variable");
        assertEquals(envDir, spillParent(new SpillFiles(null, null, envDir.toString())));
        assertEquals(Paths.get(System.getProperty("java.io.tmpdir")), spillParent(new SpillFiles(null, null, null)));
    }

    @Test
    void systemPropertyKnobSelectsTheProductionDirectory(@TempDir Path temp) throws IOException {
        Path knobDir = directory(temp, "knob");
        String original = System.setProperty("jqpdf.tmpdir", knobDir.toString());
        try {
            assertEquals(knobDir, SpillFiles.forJob(null).spill(new byte[] {1}, ".bin").getParent());
        } finally {
            if (original == null) {
                System.clearProperty("jqpdf.tmpdir");
            } else {
                System.setProperty("jqpdf.tmpdir", original);
            }
        }
    }

    @Test
    void unusableConfiguredDirectoryFailsFastNamingTheKnob(@TempDir Path temp) throws IOException {
        Path missing = temp.resolve("missing");
        Path plainFile = Files.createFile(temp.resolve("plain.txt"));

        IllegalStateException fromJobDir = assertThrows(IllegalStateException.class,
                () -> new SpillFiles(missing, null, null).spill(new byte[] {1}, ".bin"));
        assertTrue(fromJobDir.getMessage().contains("tempDir(...)"),
                "message should name the knob, got: " + fromJobDir.getMessage());

        IllegalStateException fromProperty = assertThrows(IllegalStateException.class,
                () -> new SpillFiles(null, missing.toString(), null).spill(new byte[] {1}, ".bin"));
        assertTrue(fromProperty.getMessage().contains("jqpdf.tmpdir"),
                "message should name the knob, got: " + fromProperty.getMessage());

        IllegalStateException fromEnv = assertThrows(IllegalStateException.class,
                () -> new SpillFiles(null, null, plainFile.toString()).spill(new byte[] {1}, ".bin"));
        assertTrue(fromEnv.getMessage().contains("JQPDF_TMPDIR"),
                "message should name the knob, got: " + fromEnv.getMessage());
    }

    @Test
    void spillFilesAreUniqueAndCleanupRemovesOnlyOwnFiles(@TempDir Path temp) throws IOException {
        SpillFiles spills = new SpillFiles(temp, null, null);
        Path foreign = Files.createFile(temp.resolve("foreign.txt"));

        Path fromBytes = spills.spill(new byte[] {1, 2}, ".bin");
        Path fromStream = spills.spill(new ByteArrayInputStream(new byte[] {3, 4}), ".bin");

        assertNotEquals(fromBytes, fromStream, "each spill must get its own unique file");
        assertArrayEquals(new byte[] {1, 2}, Files.readAllBytes(fromBytes));
        assertArrayEquals(new byte[] {3, 4}, Files.readAllBytes(fromStream));

        spills.cleanup();

        assertFalse(Files.exists(fromBytes), "cleanup must remove the files this run created");
        assertFalse(Files.exists(fromStream), "cleanup must remove the files this run created");
        assertTrue(Files.exists(foreign), "cleanup must not touch files it did not create");
    }

    private static Path directory(Path temp, String name) throws IOException {
        return Files.createDirectory(temp.resolve(name));
    }

    private static Path spillParent(SpillFiles spills) {
        try {
            return spills.spill(new byte[] {1}, ".bin").getParent();
        } finally {
            spills.cleanup();
        }
    }
}
