package dev.machineminds.jqpdf.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import dev.machineminds.jqpdf.Jqpdf;
import dev.machineminds.jqpdf.QpdfNative;

/**
 * Loads the native qpdf stack for the running platform exactly once per JVM:
 * the bundled companion libraries, then libqpdf itself, then the JNI shim.
 *
 * <p>Binaries live under {@code /dev/machineminds/jqpdf/native/<os>-<arch>/}
 * on the classpath (or in the jar) together with a {@code files.txt} manifest
 * written by {@code scripts/fetch-qpdf-binaries}; the shim library is added
 * by {@code scripts/build-native-shim}. Libraries are extracted into a
 * versioned temp directory before loading because neither Windows nor Linux
 * can load a shared library straight out of a jar entry.
 *
 * <p>The system property {@code jqpdf.native.dir} bypasses extraction and
 * loads an already-unpacked platform directory (development aid).
 *
 * <p>Internal API: not a stability boundary; use {@link dev.machineminds.jqpdf.QpdfNative}
 * for mocking. Public only because tests in other packages probe availability.
 */
public final class NativeLibrary {

    private static final String RESOURCE_BASE = "/dev/machineminds/jqpdf/native/";
    private static final String DEV_DIR_PROPERTY = "jqpdf.native.dir";

    private static volatile JniQpdfNative instance;

    private NativeLibrary() {
    }

    /** Returns the loaded shim binding, loading the native stack first if needed. */
    public static JniQpdfNative shim() {
        JniQpdfNative current = instance;
        if (current != null) {
            return current;
        }
        synchronized (NativeLibrary.class) {
            if (instance == null) {
                instance = loadShim();
            }
            return instance;
        }
    }

    /**
     * Whether the running platform's binaries and shim are present on the
     * classpath. Used by tests to skip (with an explanation) instead of
     * failing when the native part has not been fetched or built.
     */
    public static boolean isAvailable() {
        try {
            Platform platform = Platform.current();
            return qpdfLibraryResource(platform) != null
                    && NativeLibrary.class.getResource(resourceDir(platform) + platform.shimName) != null;
        } catch (UnsupportedPlatformException e) {
            return false;
        }
    }

    private static JniQpdfNative loadShim() {
        Platform platform = Platform.current();
        Path dir = unpackedDirectory(platform);
        List<String> manifest = manifestFor(platform);
        try {
            loadLibraries(platform, dir, manifest);
        } catch (UnsatisfiedLinkError e) {
            throw new UnsatisfiedLinkError(
                    "failed to load jqpdf native stack for " + platform.directory + " from " + dir
                            + ": " + e.getMessage()
                            + " (bundled qpdf " + Jqpdf.QPDF_VERSION + ")");
        }
        JniQpdfNative shim = new JniQpdfNative();
        if (shim.nativeAbiVersion() != JniQpdfNative.EXPECTED_ABI) {
            throw new UnsatisfiedLinkError(
                    "jqpdf shim ABI " + shim.nativeAbiVersion()
                            + " does not match the expected " + JniQpdfNative.EXPECTED_ABI
                            + "; rebuild the shim with scripts/build-native-shim");
        }
        return shim;
    }

    /**
     * Loads the platform libraries in dependency-safe order. Windows and
     * macOS resolve sibling dependencies through the loader's altered search
     * path once libqpdf is loaded explicitly; on Linux, shared objects have
     * no such mechanism, so every companion library is dlopen'ed explicitly,
     * retrying until no further progress is possible (dependency order is
     * not known ahead of time).
     */
    private static void loadLibraries(Platform platform, Path dir, List<String> manifest) {
        if (platform.os.equals("linux")) {
            loadInDependencyOrder(dir, companions(manifest, platform));
        }
        for (String qpdfLib : qpdfLibraries(manifest, platform)) {
            System.load(dir.resolve(qpdfLib).toString());
        }
        System.load(dir.resolve(platform.shimName).toString());
    }

    private static List<String> companions(List<String> manifest, Platform platform) {
        List<String> companions = new ArrayList<>();
        for (String file : manifest) {
            if (!file.startsWith("libqpdf") && !file.equals(platform.shimName)) {
                companions.add(file);
            }
        }
        return companions;
    }

    private static List<String> qpdfLibraries(List<String> manifest, Platform platform) {
        List<String> qpdf = new ArrayList<>();
        for (String file : manifest) {
            boolean isQpdf = file.startsWith("qpdf") || file.startsWith("libqpdf");
            if (isQpdf && !file.equals(platform.shimName)) {
                qpdf.add(file);
            }
        }
        return qpdf;
    }

    private static void loadInDependencyOrder(Path dir, List<String> remaining) {
        List<String> pending = new ArrayList<>(remaining);
        while (!pending.isEmpty()) {
            boolean progress = false;
            for (String file : new ArrayList<>(pending)) {
                try {
                    System.load(dir.resolve(file).toString());
                    pending.remove(file);
                    progress = true;
                } catch (UnsatisfiedLinkError e) {
                    // A dependent library whose prerequisites are not loaded
                    // yet; retried in the next round.
                }
            }
            if (!progress) {
                throw new UnsatisfiedLinkError(
                        "cannot resolve dependencies for: " + String.join(", ", pending));
            }
        }
    }

    private static Path unpackedDirectory(Platform platform) {
        String devDir = System.getProperty(DEV_DIR_PROPERTY);
        if (devDir != null && !devDir.isEmpty()) {
            return Path.of(devDir);
        }
        Path dir = Path.of(System.getProperty("java.io.tmpdir"),
                "jqpdf-native-" + Jqpdf.QPDF_VERSION + "-" + platform.directory);
        Path marker = dir.resolve(".complete");
        String fingerprint = resourceFingerprint(platform);
        if (!Files.isRegularFile(marker) || !fingerprint.equals(readMarker(marker))) {
            try {
                recreate(dir);
                for (String file : manifestFor(platform)) {
                    try (InputStream in = requiredResource(resourceDir(platform) + file)) {
                        Files.copy(in, dir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                for (String file : new String[] {platform.shimName, "files.txt"}) {
                    String resource = resourceDir(platform) + file;
                    if (NativeLibrary.class.getResource(resource) != null) {
                        try (InputStream in = requiredResource(resource)) {
                            Files.copy(in, dir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                Files.writeString(marker, fingerprint);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot extract native libraries to " + dir, e);
            }
        }
        return dir;
    }

    /**
     * Cheap fingerprint of everything that gets extracted; a rebuilt qpdf or
     * shim changes it and forces re-extraction instead of loading stale
     * binaries left in the temp directory by a previous run.
     */
    private static String resourceFingerprint(Platform platform) {
        StringBuilder fingerprint = new StringBuilder(
                Jqpdf.QPDF_VERSION + ":abi" + QpdfNative.EXPECTED_ABI);
        for (String file : readManifestResource(platform)) {
            fingerprint.append(':').append(file).append('=')
                    .append(resourceSize(resourceDir(platform) + file));
        }
        fingerprint.append(':').append(platform.shimName).append('=')
                .append(resourceSize(resourceDir(platform) + platform.shimName));
        return fingerprint.toString();
    }

    private static long resourceSize(String resource) {
        try (InputStream in = NativeLibrary.class.getResourceAsStream(resource)) {
            if (in == null) {
                return -1;
            }
            long total = 0;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
            }
            return total;
        } catch (IOException e) {
            return -1;
        }
    }

    private static String readMarker(Path marker) {
        try {
            return Files.readString(marker);
        } catch (IOException e) {
            return "";
        }
    }

    private static void recreate(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (Path file : files.collect(java.util.stream.Collectors.toList())) {
                    Files.deleteIfExists(file);
                }
            }
        } else {
            Files.createDirectories(dir);
        }
    }

    private static List<String> manifestFor(Platform platform) {
        String devDir = System.getProperty(DEV_DIR_PROPERTY);
        if (devDir != null && !devDir.isEmpty()) {
            return devManifest(Path.of(devDir));
        }
        return readManifestResource(platform);
    }

    private static List<String> readManifestResource(Platform platform) {
        try (InputStream in = NativeLibrary.class.getResourceAsStream(
                resourceDir(platform) + "files.txt")) {
            if (in == null) {
                return List.of();
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> files = new ArrayList<>();
            for (String line : content.split("\n")) {
                if (!line.isBlank()) {
                    files.add(line.trim());
                }
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read native manifest", e);
        }
    }

    private static List<String> devManifest(Path dir) {
        try {
            List<String> files = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                stream.map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".dll") || name.endsWith(".so")
                                || name.contains(".so.") || name.contains(".dylib"))
                        .sorted()
                        .forEach(files::add);
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + dir, e);
        }
    }

    private static InputStream requiredResource(String resource) {
        InputStream in = NativeLibrary.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("missing native resource " + resource);
        }
        return in;
    }

    private static String qpdfLibraryResource(Platform platform) {
        for (String file : readManifestResource(platform)) {
            if (file.startsWith("qpdf") || file.startsWith("libqpdf")) {
                return file;
            }
        }
        return null;
    }

    private static String resourceDir(Platform platform) {
        return RESOURCE_BASE + platform.directory + "/";
    }

    private static final class Platform {

        final String os;
        final String arch;
        final String directory;
        final String shimName;

        private Platform(String os, String arch, String shimName) {
            this.os = os;
            this.arch = arch;
            this.directory = os + "-" + arch;
            this.shimName = shimName;
        }

        static Platform current() {
            String os = System.getProperty("os.name", "").toLowerCase();
            String arch = System.getProperty("os.arch", "").toLowerCase();
            if (os.contains("win")) {
                return new Platform("windows", normalizeArch(arch), "jqpdfjni.dll");
            }
            if (os.contains("mac") || os.contains("darwin")) {
                return new Platform("macos", normalizeArch(arch), "libjqpdfjni.dylib");
            }
            if (os.contains("linux")) {
                return new Platform("linux", normalizeArch(arch), "libjqpdfjni.so");
            }
            throw new UnsupportedPlatformException(os + "-" + arch);
        }

        private static String normalizeArch(String arch) {
            switch (arch) {
                case "amd64":
                case "x86_64":
                    return "x86_64";
                case "x86":
                case "i386":
                    return "x86";
                case "aarch64":
                case "arm64":
                    return "aarch64";
                default:
                    throw new UnsupportedPlatformException(arch);
            }
        }
    }

    static final class UnsupportedPlatformException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        UnsupportedPlatformException(String platform) {
            super("unsupported platform for the bundled qpdf binaries: " + platform);
        }
    }
}
