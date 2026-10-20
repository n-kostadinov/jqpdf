package dev.machineminds.jqpdf.options;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * Advanced control options (manual section "Advanced Control Options"):
 * expert password handling and parser behavior on damaged or hybrid files.
 */
public interface AdvancedControlOptions {

    /** Interpretation of {@code password(...)} for the main input. */
    enum PasswordMode {
        AUTO, UNICODE, BYTES, HEX_BYTES;

        public String cliValue() {
            return name().toLowerCase().replace('_', '-');
        }
    }

    /**
     * {@code --password-is-hex-key}: treat the password as a hex-encoded
     * encryption key, bypassing password-to-key computation. Expert option;
     * applies to the main input only.
     */
    QpdfJob passwordIsHexKey();

    /** {@code --suppress-password-recovery}: disable password re-encoding retries. */
    QpdfJob suppressPasswordRecovery();

    /** {@code --password-mode=mode}: how supplied passwords are interpreted. */
    QpdfJob passwordMode(PasswordMode mode);

    /** {@code --suppress-recovery}: fail on damaged files instead of repairing. */
    QpdfJob suppressRecovery();

    /** {@code --ignore-xref-streams}: fall back to xref tables in hybrid files. */
    QpdfJob ignoreXrefStreams();
}
