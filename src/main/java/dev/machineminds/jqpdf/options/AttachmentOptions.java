package dev.machineminds.jqpdf.options;

import java.util.function.Consumer;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * Embedded-file (attachment) options (manual section "Embedded Files"):
 * attaching, copying, removing, listing, and extracting attachments.
 */
public interface AttachmentOptions {

    /**
     * {@code --add-attachment …​ --}: embed a file. The consumer configures
     * the group; key/filename default to the file's basename. Repeatable
     * across multiple {@code addAttachment} calls.
     */
    QpdfJob addAttachment(Consumer<AttachmentConfig> config);

    /** One {@code --add-attachment …​ --} group (spec §5.11). */
    interface AttachmentConfig {

        /** The file to embed; exactly one of {@code file} or {@link #content(byte[])} is required. */
        AttachmentConfig file(String file);

        /**
         * An in-memory attachment payload, spilled to a temporary file for
         * the duration of the next {@code run()}. Requires {@link #key(String)}
         * (no file basename exists to default it from) and excludes
         * {@link #file(String)} (spec §9.1).
         */
        AttachmentConfig content(byte[] content);

        /** Key in the embedded-files name tree; default: basename of the file. */
        AttachmentConfig key(String key);

        /** Displayed/saved-as filename; default: basename of the file. */
        AttachmentConfig filename(String filename);

        /** Creation date in PDF format, e.g. {@code D:20210207211528Z} (§10.8). */
        AttachmentConfig creationdate(String pdfDate);

        /** Modification date in PDF format. */
        AttachmentConfig moddate(String pdfDate);

        /** MIME type stored as /Subtype, e.g. {@code application/pdf}. */
        AttachmentConfig mimetype(String type);

        /** Descriptive text shown by some viewers. */
        AttachmentConfig description(String text);

        /** Replace an existing attachment with the same key. */
        AttachmentConfig replace();
    }

    /** {@code --copy-attachments-from …​ --}: copy all attachments of another file. */
    QpdfJob copyAttachmentsFrom(Consumer<CopyAttachmentsConfig> config);

    /** One {@code --copy-attachments-from …​ --} group. */
    interface CopyAttachmentsConfig {

        /** The file whose attachments are copied (required). */
        CopyAttachmentsConfig file(String file);

        /** Prefix prepended to copied attachment keys to resolve conflicts. */
        CopyAttachmentsConfig prefix(String prefix);

        /** Password for {@code file} if it is encrypted. */
        CopyAttachmentsConfig password(String password);
    }

    /** {@code --remove-attachment=key}: repeatable; removes the attachment and its file spec. */
    QpdfJob removeAttachment(String key);

    /** {@code --list-attachments}: keys and stream numbers to the info output. */
    QpdfJob listAttachments();

    /**
     * {@code --show-attachment=key}: the attachment's bytes arrive in
     * {@link dev.machineminds.jqpdf.JobResult#savedData()}.
     */
    QpdfJob showAttachment(String key);
}
