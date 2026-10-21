package dev.machineminds.jqpdf.options;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * Structural PDF transformation options (manual section "PDF Transformation"):
 * linearization, QDF mode, stream compression, object streams, and version
 * control. These change how the file is written, not what it renders.
 */
public interface TransformationOptions {

    /** {@code --linearize}: create a web-optimized (fast web view) file. */
    QpdfJob linearize();

    /** {@code --qdf}: create a QDF file suitable for text-editor inspection. */
    QpdfJob qdf();

    /** {@code --no-original-object-ids}: omit original-ID comments in QDF output. */
    QpdfJob noOriginalObjectIds();

    /** {@code --compress-streams=y|n}: compress (flate) streams after decoding. */
    QpdfJob compressStreams(boolean compress);

    /** Which streams qpdf attempts to decode. */
    enum DecodeLevel {
        /** Decode nothing. */
        NONE,
        /** Generalized filters: {@code /FlateDecode}, {@code /LZWDecode}, {@code /ASCII85Decode}, {@code /ASCIIHexDecode} (default). */
        GENERALIZED,
        /** Generalized plus non-lossy specialized filters ({@code /RunLengthDecode}). */
        SPECIALIZED,
        /** Specialized plus lossy filters ({@code /DCTDecode}, i.e. JPEG). */
        ALL;

        /** The CLI token this value maps to. */
        public String cliValue() {
            return name().toLowerCase();
        }
    }

    /** {@code --decode-level=level}: none, generalized (default), specialized, all. */
    QpdfJob decodeLevel(DecodeLevel level);

    /** Legacy combined compression control. */
    enum StreamData {
        /** Compress streams, decoding at the generalized level (default). */
        COMPRESS,
        /** Leave streams exactly as in the input. */
        PRESERVE,
        /** Decode streams but write them uncompressed (QDF-friendly). */
        UNCOMPRESS;

        /** The CLI token this value maps to. */
        public String cliValue() {
            return name().toLowerCase();
        }
    }

    /** {@code --stream-data=mode}: legacy combined compress/preserve/uncompress. */
    QpdfJob streamData(StreamData mode);

    /** {@code --recompress-flate}: recompress already-flate streams (slow). */
    QpdfJob recompressFlate();

    /** {@code --compression-level=n}: flate level 1 (fastest) … 9 (smallest). */
    QpdfJob compressionLevel(int level);

    /** {@code --jpeg-quality=n}: JPEG quality 0–100 for optimized images. */
    QpdfJob jpegQuality(int quality);

    /** {@code --normalize-content=y|n}: normalize newlines in content streams. */
    QpdfJob normalizeContent(boolean normalize);

    /** Object stream handling. */
    enum ObjectStreams {
        /** Keep object streams as in the input (default). */
        PRESERVE,
        /** Write all objects as regular top-level objects (older viewers). */
        DISABLE,
        /** Create object streams and compress where possible; forces PDF ≥ 1.5. */
        GENERATE;

        /** The CLI token this value maps to. */
        public String cliValue() {
            return name().toLowerCase();
        }
    }

    /** {@code --object-streams=mode}: preserve, disable, or generate. */
    QpdfJob objectStreams(ObjectStreams mode);

    /** {@code --preserve-unreferenced}: keep objects not reachable from the trailer. */
    QpdfJob preserveUnreferenced();

    /** {@code --remove-unreferenced-resources=auto|yes|no}: strip unused page resources. */
    enum UnreferencedResources {
        /** Heuristic check first, full analysis only when it hints at shared resources (default). */
        AUTO,
        /** Always run the full unused-resource analysis. */
        YES,
        /** Disable removal. */
        NO;

        /** The CLI token this value maps to. */
        public String cliValue() {
            return name().toLowerCase();
        }
    }

    /** {@code --remove-unreferenced-resources=mode}: auto (default), yes, no. */
    QpdfJob removeUnreferencedResources(UnreferencedResources mode);

    /** {@code --newline-before-endstream}: PDF/A preservation requirement. */
    QpdfJob newlineBeforeEndstream();

    /** {@code --coalesce-contents}: join each page's content streams into one. */
    QpdfJob coalesceContents();

    /** {@code --externalize-inline-images}: convert inline images to objects. */
    QpdfJob externalizeInlineImages();

    /** {@code --ii-min-bytes=n}: minimum size for externalized inline images. */
    QpdfJob iiMinBytes(long bytes);

    /** {@code --min-version=version}: raise output version to at least this. */
    QpdfJob minVersion(String version);

    /** {@code --force-version=version}: force the version header (may lose features). */
    QpdfJob forceVersion(String version);
}
