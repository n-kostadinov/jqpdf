package dev.machineminds.jqpdf.test;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;

/** Access to the qpdf checkout's test PDFs and small helpers shared by tests. */
public final class Fixtures {

    /** Root of the qpdf CLI test fixtures (the sibling qpdf checkout). */
    public static final Path QPDF_TEST_PDFS = Path.of("..", "qpdf", "qpdf", "qtest", "qpdf");

    /** Root of the qpdf checkout itself, for fixtures in other test trees. */
    public static final Path QPDF_CHECKOUT = Path.of("..", "qpdf");

    private Fixtures() {
    }

    /** A named fixture from {@link #QPDF_TEST_PDFS}, skipping the test if absent. */
    public static Path assumePdf(String name) {
        Path pdf = QPDF_TEST_PDFS.resolve(name);
        Assumptions.assumeTrue(Files.isRegularFile(pdf),
                "fixture not found: " + pdf + " (qpdf checkout expected as ../qpdf)");
        return pdf;
    }

    /** A fixture anywhere in the qpdf checkout, given as a checkout-relative
     * path (e.g. {@code examples/qtest/overlay-page/in.pdf}). */
    public static Path assumeQpdfFile(String relativePath) {
        Path file = QPDF_CHECKOUT.resolve(relativePath);
        Assumptions.assumeTrue(Files.isRegularFile(file),
                "fixture not found: " + file + " (qpdf checkout expected as ../qpdf)");
        return file;
    }

    /** True when the running platform's native stack is available. */
    public static boolean nativeAvailable() {
        return dev.machineminds.jqpdf.internal.NativeLibrary.isAvailable();
    }

    /** Encodes argv for {@code QpdfNative.jobInitializeFromArgv}: each string
     * as real UTF-8 followed by one NUL separator. */
    public static byte[] encodeArgv(String... argv) {
        return dev.machineminds.jqpdf.internal.ArgvCodec.encode(argv);
    }
}
