package dev.machineminds.jqpdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class JqpdfTest {

    @Test
    void versionIsDefined() {
        assertFalse(Jqpdf.VERSION.isBlank());
    }

    @Test
    void versionComesFromTheFilteredBuildResource() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Jqpdf.class
                .getResourceAsStream("/dev/machineminds/jqpdf/version.properties")) {
            assertNotNull(in, "filtered version.properties must be on the test classpath");
            properties.load(in);
        }
        assertEquals(properties.getProperty("jqpdf.version"), Jqpdf.VERSION);
    }

    @Test
    void qpdfVersionIsPinnedToMavenProperty() {
        assertEquals("12.4.2", Jqpdf.QPDF_VERSION);
    }
}
