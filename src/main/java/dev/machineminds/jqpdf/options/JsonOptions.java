package dev.machineminds.jqpdf.options;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * qpdf JSON options (manual section "JSON Options", spec §6): generating the
 * qpdf JSON representation, reading it back, and updating a PDF from it.
 * JSON output is returned as text — jqpdf deliberately ships no JSON parser.
 */
public interface JsonOptions {

    /** {@code --json}: write the qpdf JSON representation (latest version). */
    QpdfJob json();

    /** {@code --json=1|2}: choose the JSON output version. */
    QpdfJob json(int version);

    /** {@code --json-key=key}: restrict output to named top-level keys; repeatable. */
    QpdfJob jsonKey(String key);

    /** {@code --json-object=trailer|obj[,gen]}: restrict the objects dictionary; repeatable. */
    QpdfJob jsonObject(String objectSpec);

    /** Where stream data goes in JSON output. */
    enum JsonStreamData {
        /** Omit stream data (default; {@code jsonOutput()} upgrades this to inline). */
        NONE,
        /** Base64 stream data in each stream object's {@code data} key. */
        INLINE,
        /** Each stream written to a {@code <prefix>-<object-number>} file; see {@link #jsonStreamPrefix(String)}. */
        FILE;

        /** The CLI token this value maps to. */
        public String cliValue() {
            return name().toLowerCase();
        }
    }

    /** {@code --json-stream-data=none|inline|file}. */
    QpdfJob jsonStreamData(JsonStreamData mode);

    /** {@code --json-stream-prefix=prefix}: file prefix for json-stream-data=file. */
    QpdfJob jsonStreamPrefix(String prefix);

    /** {@code --json-output}: faithful-serialization presets (implies --json). */
    QpdfJob jsonOutput();

    /** {@code --json-output=2}: the only version with faithful-serialization presets. */
    QpdfJob jsonOutput(int version);

    /** {@code --json-input}: treat the input file as qpdf JSON. */
    QpdfJob jsonInput();

    /**
     * {@code --json-input} with the document supplied in memory: the JSON
     * text is spilled to a temporary file that serves as the input for the
     * next {@code run()} (spec §9.1). May not be combined with
     * {@code replaceInput()}.
     */
    QpdfJob jsonInputContent(String json);

    /** Byte form of {@link #jsonInputContent(String)}. */
    QpdfJob jsonInputContent(byte[] json);

    /** {@code --update-from-json=file}: update the input PDF's objects from qpdf JSON. */
    QpdfJob updateFromJson(String file);

    /**
     * {@code --update-from-json} with the document supplied in memory,
     * spilled to a temporary file for the duration of the next
     * {@code run()} (spec §9.1).
     */
    QpdfJob updateFromJsonContent(String json);

    /** Byte form of {@link #updateFromJsonContent(String)}. */
    QpdfJob updateFromJsonContent(byte[] json);
}
