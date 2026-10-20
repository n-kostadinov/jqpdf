package dev.machineminds.jqpdf.options;

import java.util.function.Consumer;

import dev.machineminds.jqpdf.QpdfJob;

/**
 * Overlay and underlay options (manual section "Overlay and Underlay"):
 * drawing another file's pages on top of (overlay) or beneath (underlay)
 * the output pages.
 */
public interface OverlayOptions {

    /**
     * {@code --overlay …​ --}: draw the configured file's pages on top of
     * the output pages.
     */
    QpdfJob overlay(Consumer<OverlayConfig> config);

    /** {@code --underlay …​ --}: draw beneath the output pages. */
    QpdfJob underlay(Consumer<OverlayConfig> config);

    /** One overlay/underlay group (spec §5.9). */
    interface OverlayConfig {

        OverlayConfig file(String file);

        OverlayConfig password(String password);

        /** Destination pages in the output; default all. */
        OverlayConfig to(String pageRange);

        /** Source pages, consumed in order; default all. */
        OverlayConfig from(String pageRange);

        /** Pages cycled after the {@code from} pages are exhausted. */
        OverlayConfig repeat(String pageRange);
    }
}
