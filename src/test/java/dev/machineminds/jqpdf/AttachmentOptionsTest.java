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

    @Test
    void attachmentMetadataIsRecorded(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path attached = temp.resolve("note.txt");
        Files.write(attached, "payload".getBytes(StandardCharsets.UTF_8));
        Path withAttachment = temp.resolve("with-attachment.pdf");

        Jqpdf.createJob()
                .input(input.toString()).output(withAttachment.toString())
                .addAttachment(a -> a
                        .file(attached.toString())
                        .key("note.txt")
                        .filename("display-name.txt")
                        .description("a note")
                        .mimetype("text/plain")
                        .creationdate("D:20210207211528Z")
                        .moddate("D:20210208120000Z"))
                .run().orThrow();

        String listing = Jqpdf.createJob()
                .input(withAttachment.toString())
                .listAttachments().verbose()
                .run().orThrow().infoText();
        assertTrue(listing.contains("description: a note"),
                "the description is listed, got: " + listing);
        assertTrue(listing.contains("preferred name: display-name.txt"),
                "the display filename differs from the key, got: " + listing);
        assertTrue(listing.contains("mime type: text/plain"),
                "the mime type is stored, got: " + listing);
        assertTrue(listing.contains("creation date: D:20210207211528")
                        && listing.contains("modification date: D:20210208120000"),
                "the supplied PDF dates are stored, got: " + listing);
    }

    @Test
    void replaceAttachmentResolvesDuplicateKeys(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        Path attached = temp.resolve("note.txt");
        Files.write(attached, "payload".getBytes(StandardCharsets.UTF_8));

        JobResult rejected = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("dup.pdf").toString())
                .addAttachment(a -> a.file(attached.toString()).key("note.txt"))
                .addAttachment(a -> a.file(attached.toString()).key("note.txt"))
                .run();
        assertEquals(2, rejected.exitCode(),
                "a duplicate key is an error without --replace; error: "
                        + rejected.errorText());

        JobResult replaced = Jqpdf.createJob()
                .input(input.toString()).output(temp.resolve("dup.pdf").toString())
                .addAttachment(a -> a.file(attached.toString()).key("note.txt"))
                .addAttachment(a -> a.file(attached.toString()).key("note.txt").replace())
                .run();
        assertEquals(0, replaced.exitCode(),
                "--replace allows the duplicate key; error: " + replaced.errorText());
        assertEquals(1, countListedAttachments(
                Jqpdf.createJob().input(temp.resolve("dup.pdf").toString())
                        .listAttachments().run().orThrow().infoText()),
                "exactly one attachment with that key remains");
    }

    @Test
    void copyAttachmentsFromWithPrefixResolvesConflicts(@TempDir Path temp) throws Exception {
        assumeTrue(nativeAvailable(), "native stack not available");
        Path input = assumePdf("11-pages.pdf");
        byte[] payload = "copy me".getBytes(StandardCharsets.UTF_8);
        Path attached = temp.resolve("note.txt");
        Files.write(attached, payload);
        Path source = temp.resolve("source.pdf");
        attachNote(input, attached, source);
        Path target = temp.resolve("target.pdf");
        attachNote(input, attached, target);

        JobResult rejected = Jqpdf.createJob()
                .input(target.toString()).output(temp.resolve("copied.pdf").toString())
                .copyAttachmentsFrom(c -> c.file(source.toString()))
                .run();
        assertEquals(2, rejected.exitCode(),
                "copying onto a conflicting key is an error; error: " + rejected.errorText());

        Path copied = temp.resolve("copied.pdf");
        Jqpdf.createJob()
                .input(target.toString()).output(copied.toString())
                .copyAttachmentsFrom(c -> c.file(source.toString()).prefix("copy-"))
                .run().orThrow();

        String listing = Jqpdf.createJob().input(copied.toString())
                .listAttachments().run().orThrow().infoText();
        assertTrue(listing.contains("note.txt") && listing.contains("copy-note.txt"),
                "the prefix keeps both attachments distinct, got: " + listing);
        assertArrayEquals(payload,
                Jqpdf.createJob().input(copied.toString())
                        .showAttachment("copy-note.txt").run().orThrow().savedData(),
                "the copied attachment carries the source's bytes");
    }

    private static void attachNote(Path input, Path attached, Path output) {
        Jqpdf.createJob()
                .input(input.toString()).output(output.toString())
                .addAttachment(a -> a.file(attached.toString()).key("note.txt"))
                .run().orThrow();
    }

    private static int countListedAttachments(String listing) {
        return (int) listing.lines().filter(line -> line.contains(" -> ")).count();
    }
}
