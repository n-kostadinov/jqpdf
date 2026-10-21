package dev.machineminds.jqpdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Java equivalents of the CLI's help/information options. These must never
 * be routed through the native argv interface: qpdf's argument parser calls
 * exit(0) for help options, which would terminate the hosting JVM.
 */
class JqpdfInfoTest {

    @Test
    void qpdfVersionMatchesBundledLibrary() {
        assertEquals("qpdf version " + Jqpdf.QPDF_VERSION, Jqpdf.qpdfVersion());
        assertEquals("qpdf version 12.4.2", Jqpdf.qpdfVersion());
    }

    @Test
    void qpdfCopyrightCarriesNoticeAndLicense() {
        String copyright = Jqpdf.qpdfCopyright();
        assertTrue(copyright.contains("Jay Berkenbilt"), "copyright notice should be present");
        assertTrue(copyright.contains("Apache License"), "license text should be embedded");
        assertTrue(copyright.contains("Version 2.0"), "the Apache 2.0 header should be present");
    }
}
