package dev.machineminds.jqpdf.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Encodes argv for {@code QpdfNative.jobInitializeFromArgv}: each string as
 * real UTF-8 followed by one NUL separator (the C shim splits the blob).
 */
public final class ArgvCodec {

    private ArgvCodec() {
    }

    public static byte[] encode(List<String> argv) {
        return encode(argv.toArray(new String[0]));
    }

    public static byte[] encode(String... argv) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (String arg : argv) {
                out.write(arg.getBytes(StandardCharsets.UTF_8));
                out.write(0);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Splits a decoded blob back into its arguments (test aid). */
    public static List<String> decode(byte[] blob) {
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        int start = 0;
        for (int i = 0; i <= blob.length; i++) {
            if (i == blob.length || blob[i] == 0) {
                if (i > start) {
                    args.add(new String(blob, start, i - start, StandardCharsets.UTF_8));
                }
                start = i + 1;
            }
        }
        return args;
    }
}
