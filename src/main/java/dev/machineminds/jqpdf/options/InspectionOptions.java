package dev.machineminds.jqpdf.options;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * Inspection options (manual section "PDF Inspection"): jobs that report
 * information instead of writing an output file. Text results arrive in
 * {@link dev.machineminds.jqpdf.JobResult#infoText()}; binary stream output
 * ({@code rawStreamData}/{@code filteredStreamData}) in
 * {@link dev.machineminds.jqpdf.JobResult#savedData()}.
 */
public interface InspectionOptions {

    /**
     * {@code --is-encrypted}: exit code reports encryption — 0 encrypted,
     * 2 not. Works without the password; all other options are ignored.
     */
    QpdfJob isEncrypted();

    /**
     * {@code --requires-password}: exit code 0 = a password other than the
     * supplied one is required, 2 = not encrypted, 3 = the supplied
     * {@code password(...)} is correct.
     */
    QpdfJob requiresPassword();

    /** {@code --check}: structure, encryption, linearization, and stream checks. */
    QpdfJob check();

    /** {@code --show-encryption}: print encryption parameters. */
    QpdfJob showEncryption();

    /** {@code --show-encryption-key}: include the encryption key as hex. */
    QpdfJob showEncryptionKey();

    /** {@code --check-linearization}: check hint tables of a linearized file. */
    QpdfJob checkLinearization();

    /** {@code --show-linearization}: dump the linearization hint tables. */
    QpdfJob showLinearization();

    /** {@code --show-xref}: dump the cross-reference table/stream. */
    QpdfJob showXref();

    /** {@code --show-object=trailer|obj[,gen]}: print an object; repeatable. */
    QpdfJob showObject(String objectSpec);

    /** {@code --raw-stream-data}: with showObject on a stream: raw bytes to savedData. */
    QpdfJob rawStreamData();

    /** {@code --filtered-stream-data}: with showObject on a stream: decoded bytes. */
    QpdfJob filteredStreamData();

    /** {@code --show-npages}: page count as a single line of info text. */
    QpdfJob showNpages();

    /** {@code --show-pages}: per-page object and content stream numbers. */
    QpdfJob showPages();

    /** {@code --with-images}: include image object IDs in showPages output. */
    QpdfJob withImages();
}
