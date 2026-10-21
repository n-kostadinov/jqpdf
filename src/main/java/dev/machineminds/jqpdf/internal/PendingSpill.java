package dev.machineminds.jqpdf.internal;

/**
 * An option token whose value is caller-supplied bytes (e.g. an
 * {@code --update-from-json} document or an attachment payload): spilled to a
 * file by the owning run's {@link SpillFiles} and rendered as
 * {@code prefix<path>} at argv-synthesis time, so the file's lifecycle stays
 * tied to a single {@code run()}.
 */
final class PendingSpill {

    private final byte[] content;
    private final String prefix;
    private final String suffix;

    PendingSpill(byte[] content, String prefix, String suffix) {
        this.content = content;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    String render(SpillFiles spills) {
        return prefix + spills.spill(content, suffix);
    }
}
