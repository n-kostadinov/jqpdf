package dev.machineminds.jqpdf.options;

import java.util.function.Consumer;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * PDF modification options (manual section "PDF Modification"): page
 * selection, rotation, splitting, collation, labels, appearance handling,
 * image optimization, and the remove-* family. These change how the
 * document renders.
 */
public interface ModificationOptions {

    /**
     * {@code --pages …​ --}: begin page selection for split/merge/collate.
     * Each {@code file(...)} starts a page group; {@code range(...)} and
     * {@code password(...)} apply to the most recent file. {@code "."} refers
     * to the primary input. Document-level data comes from the primary input
     * (spec §10.6).
     */
    QpdfJob pages(Consumer<PagesGroup> specs);

    /** One {@code --pages …​ --} group's file/range/password entries. */
    interface PagesGroup {

        PagesGroup file(String file);

        PagesGroup range(String pageRange);

        PagesGroup password(String password);
    }

    /** {@code --collate}: interleave pages round-robin from each page group. */
    QpdfJob collate();

    /** {@code --collate=n[,m,…]}: group sizes per page group while collating. */
    QpdfJob collate(String groupSizes);

    /** {@code --split-pages}: one page per output file ({@code %d} in the output name). */
    QpdfJob splitPages();

    /** {@code --split-pages=n}: n pages per output file. */
    QpdfJob splitPages(int groupSize);

    /** {@code --flatten-rotation}: bake /Rotate into page contents. */
    QpdfJob flattenRotation();

    /** Which annotations {@code --flatten-annotations} pushes into page content. */
    enum FlattenAnnotations {
        ALL, PRINT, SCREEN;

        public String cliValue() {
            return name().toLowerCase();
        }
    }

    /** {@code --flatten-annotations=all|print|screen}: freeze annotation placement. */
    QpdfJob flattenAnnotations(FlattenAnnotations scope);

    /** {@code --generate-appearances}: regenerate stale form-field appearances. */
    QpdfJob generateAppearances();

    /** {@code --optimize-images}: recompress non-JPEG images as JPEG when smaller. */
    QpdfJob optimizeImages();

    /** {@code --oi-min-width=n}: skip image optimization below this width. */
    QpdfJob oiMinWidth(int width);

    /** {@code --oi-min-height=n}: skip image optimization below this height. */
    QpdfJob oiMinHeight(int height);

    /** {@code --oi-min-area=n}: skip image optimization below this pixel area. */
    QpdfJob oiMinArea(long area);

    /** {@code --keep-inline-images}: exclude inline images from optimization. */
    QpdfJob keepInlineImages();

    /** {@code --remove-acroform}: remove the interactive form dictionary. */
    QpdfJob removeAcroform();

    /** {@code --remove-info}: omit all /Info entries except /ModDate. */
    QpdfJob removeInfo();

    /** {@code --remove-metadata}: omit the catalog's XMP metadata stream. */
    QpdfJob removeMetadata();

    /** {@code --remove-page-labels}: omit /PageLabels from the catalog. */
    QpdfJob removePageLabels();

    /** {@code --remove-structure}: omit the structure tree and /MarkInfo. */
    QpdfJob removeStructure();

    /**
     * {@code --rotate=[+|-]angle[:page-range]}: repeatable; bare angle sets
     * absolute rotation, {@code +}/{@code -} rotate relatively; omitted
     * range means all pages. Angles: 0, 90, 180, 270.
     */
    QpdfJob rotate(String spec);

    /**
     * {@code --set-page-labels spec …​ --}: page labels for the whole
     * document; first spec must start at page 1, specs in increasing order.
     * Grammar: {@code first-page:[type][/start[/prefix]]} (spec §10.7).
     */
    QpdfJob setPageLabels(String... specs);
}
