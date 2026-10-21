package dev.machineminds.jqpdf;

import dev.machineminds.jqpdf.internal.NativeLibrary;
import dev.machineminds.jqpdf.internal.QpdfJobImpl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Entry point of the jqpdf library: version constants and the job factory.
 * See {@link QpdfJob} for the job model; the package documentation gives an
 * orientation with links to the guides and the CLI specification.
 */
public final class Jqpdf {

    /**
     * Version of the jqpdf library. Injected from the Maven
     * {@code project.version} via the same filtered resource as
     * {@link #QPDF_VERSION}, so the constant always matches the jar it
     * ships in.
     */
    public static final String VERSION = loadVersionProperty("jqpdf.version");

    /**
     * Version of the bundled native qpdf library. Injected from the Maven
     * property {@code qpdf.version} via a filtered resource so the jar, the
     * downloaded binaries, and the compiled JNI shim always agree.
     */
    public static final String QPDF_VERSION = loadVersionProperty("qpdf.version");

    /**
     * Creates a job backed by the bundled native qpdf binaries. The native
     * stack is loaded on first use and shared by all jobs.
     */
    public static QpdfJob createJob() {
        return createJob(NativeLibrary.shim());
    }

    /**
     * Creates a job driven by a caller-supplied {@link QpdfNative} adapter —
     * the seam for tests and alternative bindings.
     */
    public static QpdfJob createJob(QpdfNative qpdf) {
        return new QpdfJobImpl(qpdf);
    }

    /**
     * Equivalent of the CLI's {@code --version}: {@code qpdf version x.y.z}
     * for the bundled native library.
     */
    public static String qpdfVersion() {
        return "qpdf version " + QPDF_VERSION;
    }

    /**
     * Equivalent of the CLI's {@code --copyright}: the qpdf copyright notice
     * and the Apache License 2.0 text.
     */
    public static String qpdfCopyright() {
        try (InputStream in = Jqpdf.class
                .getResourceAsStream("/dev/machineminds/jqpdf/qpdf-copyright.txt")) {
            if (in == null) {
                return "unknown";
            }
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "unknown";
        }
    }

    private static String loadVersionProperty(String key) {
        Properties properties = new Properties();
        try (InputStream in = Jqpdf.class
                .getResourceAsStream("/dev/machineminds/jqpdf/version.properties")) {
            if (in == null) {
                return "unknown";
            }
            properties.load(in);
        } catch (IOException e) {
            return "unknown";
        }
        return properties.getProperty(key, "unknown");
    }

    private Jqpdf() {
    }
}

