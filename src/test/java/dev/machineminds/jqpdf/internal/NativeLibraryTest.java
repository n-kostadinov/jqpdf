package dev.machineminds.jqpdf.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class NativeLibraryTest {

    @Test
    void loadsNativeStackOnceAndReportsExpectedAbi() {
        assumeTrue(NativeLibrary.isAvailable(),
                "native binaries or JNI shim not fetched/built for this platform; "
                        + "run scripts/fetch-qpdf-binaries and rebuild");

        JniQpdfNative shim = NativeLibrary.shim();
        assertEquals(JniQpdfNative.EXPECTED_ABI, shim.nativeAbiVersion());

        assertSame(shim, NativeLibrary.shim(), "second load must reuse the loaded stack");
    }
}
