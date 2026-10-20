package dev.machineminds.jqpdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class JqpdfTest {

    @Test
    void versionIsDefined() {
        assertFalse(Jqpdf.VERSION.isBlank());
    }

    @Test
    void qpdfVersionIsPinnedToMavenProperty() {
        assertEquals("12.4.2", Jqpdf.QPDF_VERSION);
    }
}
