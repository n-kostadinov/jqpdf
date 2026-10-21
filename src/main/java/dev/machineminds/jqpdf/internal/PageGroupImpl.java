package dev.machineminds.jqpdf.internal;

import java.util.ArrayList;
import java.util.List;

import dev.machineminds.jqpdf.QpdfUsageException;
import dev.machineminds.jqpdf.options.ModificationOptions.PagesGroup;
import dev.machineminds.jqpdf.options.OverlayOptions.OverlayConfig;

/**
 * Collects one grouped option block ({@code --pages} or
 * {@code --overlay}/{@code --underlay}): entries keep call order and the
 * caller terminates the group with a bare {@code --}.
 */
public final class PageGroupImpl implements PagesGroup, OverlayConfig {

    private final List<String> tokens = new ArrayList<>();

    public List<String> argvTokens() {
        if (tokens.isEmpty()) {
            throw new QpdfUsageException("group must configure at least one file");
        }
        return tokens;
    }

    @Override
    public PageGroupImpl file(String file) {
        tokens.add("--file=" + require(file, "file"));
        return this;
    }

    @Override
    public PageGroupImpl range(String pageRange) {
        tokens.add("--range=" + require(pageRange, "page range"));
        return this;
    }

    @Override
    public PageGroupImpl password(String password) {
        if (password == null) {
            throw new QpdfUsageException("password must not be null");
        }
        tokens.add("--password=" + password);
        return this;
    }

    @Override
    public PageGroupImpl to(String pageRange) {
        tokens.add("--to=" + require(pageRange, "to range"));
        return this;
    }

    @Override
    public PageGroupImpl from(String pageRange) {
        // The CLI explicitly allows an empty --from= when only --repeat is used.
        if (pageRange == null) {
            throw new QpdfUsageException("from range must not be null (use \"\" for none)");
        }
        tokens.add("--from=" + pageRange);
        return this;
    }

    @Override
    public PageGroupImpl repeat(String pageRange) {
        tokens.add("--repeat=" + require(pageRange, "repeat range"));
        return this;
    }

    private static String require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new QpdfUsageException(what + " must not be blank");
        }
        return value;
    }
}
