package dev.machineminds.jqpdf;

import static dev.machineminds.jqpdf.test.Fixtures.assumePdf;
import static dev.machineminds.jqpdf.test.Fixtures.nativeAvailable;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.machineminds.jqpdf.test.FakeQpdfNative;

/** Attachment options (spec §5.11). */
class AttachmentOptionsTest {

    @Test
    void attachmentGroupsMapToTheirCliTokens() {
        FakeQpdfNative fake = new FakeQpdfNative();

        Jqpdf.createJob(fake)
                .input("in.pdf").output("out.pdf")
                .addAttachment(a -> a
                        .file("note.txt")
                        .key("note")
                        .description("a note")
                        .mimetype("text/plain")
                        .replace())
                .copyAttachmentsFrom(c -> c.file("other.pdf").prefix("p"))
                .removeAttachment("gone")
                .run();

        assertEquals(List.of(
                "in.pdf",
                "--add-attachment",
                "note.txt",
                "--key=note",
                "--description=a note",
                "--mimetype=text/plain",
                "--replace",
                "--",
                "--copy-attachments-from",
                "other.pdf",
                "--prefix=p",
                "--",
                "--remove-attachment=gone",
                "out.pdf"), fake.argvOfRun(0));
    }

    @Test
    void addListShowRemoveAttachmentRoundTrip(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        byte[] payload = "hello attachment".getBytes(StandardCharsets.UTF_8);
        Path attached = temp.resolve("note.txt");
        Files.write(attached, payload);
        Path withAttachment = temp.resolve("with-attachment.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(withAttachment.toString())
                .addAttachment(a -> a.file(attached.toString()).key("note.txt"))
                .run().orThrow();

        JobResult listing = Jqpdf.createJob()
                .input(withAttachment.toString())
                .listAttachments()
                .run().orThrow();
        assertTrue(listing.infoText().contains("note.txt"),
                "listing should show the attachment key, got: " + listing.infoText());

        JobResult shown = Jqpdf.createJob()
                .input(withAttachment.toString())
                .showAttachment("note.txt")
                .run().orThrow();
        assertArrayEquals(payload, shown.savedData(),
                "the save pipeline must deliver the attachment bytes verbatim");

        Path stripped = temp.resolve("stripped.pdf");
        Jqpdf.createJob()
                .input(withAttachment.toString()).output(stripped.toString())
                .removeAttachment("note.txt")
                .run().orThrow();

        JobResult strippedListing = Jqpdf.createJob()
                .input(stripped.toString())
                .listAttachments()
                .run().orThrow();
        assertFalse(strippedListing.infoText().contains("note.txt"),
                "removed attachment must not be listed anymore");
        assertEquals(0, strippedListing.exitCode());
    }

    @Test
    void addAttachmentRequiresAFile() {
        FakeQpdfNative fake = new FakeQpdfNative();

        org.junit.jupiter.api.Assertions.assertThrows(QpdfUsageException.class,
                () -> Jqpdf.createJob(fake).input("in.pdf").output("out.pdf")
                        .addAttachment(a -> a.key("k")).run());
        assertEquals(0, fake.runCount());
    }
}
