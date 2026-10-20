package dev.machineminds.jqpdf.options;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * Basic invocation and general qpdf options (manual sections "Basic
 * Invocation" and "General Options"): input/output selection, passwords,
 * verbosity, determinism, and file-handling hints.
 */
public interface GeneralOptions {

    /**
     * {@code --empty}: use a zero-page dummy input instead of a file. Also
     * useful with {@code pages(...)} to drop document-level data from the
     * primary input.
     */
    QpdfJob emptyInput();

    /**
     * {@code --replace-input}: write to {@code {input}.~qpdf-temp#} and
     * overwrite the input on success. Replaces {@code output(...)}; on
     * warnings the original is preserved as {@code {input}.~qpdf-orig}.
     */
    QpdfJob replaceInput();

    /** {@code --job-json-file=file}: merge a QPDFJob JSON file (repeatable). */
    QpdfJob jobJsonFile(String file);

    /** {@code --warning-exit-0}: exit 0 instead of 3 when only warnings occurred. */
    QpdfJob warningExit0();

    /** {@code --password=password}: password for the encrypted main input. */
    QpdfJob password(String password);

    /** {@code --password-file=file}: first line of the file is the password. */
    QpdfJob passwordFile(String file);

    /** {@code --verbose}: additional information about what qpdf did. */
    QpdfJob verbose();

    /** {@code --progress}: percentage progress while writing. */
    QpdfJob progress();

    /** {@code --no-warn}: suppress warning output (exit code still 3). */
    QpdfJob noWarn();

    /** {@code --deterministic-id}: derive /ID from output content digest. */
    QpdfJob deterministicId();

    /** {@code --allow-weak-crypto}: permit writing RC4-encrypted files. */
    QpdfJob allowWeakCrypto();

    /** {@code --keep-files-open=y|n}: keep merged files open or reopen on demand. */
    QpdfJob keepFilesOpen(boolean keepOpen);

    /** {@code --keep-files-open-threshold=n}: override the default of 200 files. */
    QpdfJob keepFilesOpenThreshold(int count);
}
