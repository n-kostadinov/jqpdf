package dev.machineminds.jqpdf.internal;

import dev.machineminds.jqpdf.QpdfNative;

/**
 * Native method declarations of the jqpdf JNI shim ({@code jqpdf_jni.c}),
 * loaded by {@link NativeLibrary} once per JVM. Not part of the public API;
 * the public seam is {@link QpdfNative}.
 */
final class JniQpdfNative implements QpdfNative {

    @Override
    public native int nativeAbiVersion();

    @Override
    public native long jobCreate();

    @Override
    public native void jobDestroy(long job);

    @Override
    public native int jobInitializeFromArgv(long job, byte[] argvUtf8, int argc);

    @Override
    public native int jobInitializeFromJson(long job, byte[] jsonUtf8);

    @Override
    public native int jobRun(long job);

    @Override
    public native byte[] jobGetInfoText(long job);

    @Override
    public native byte[] jobGetWarnText(long job);

    @Override
    public native byte[] jobGetErrorText(long job);

    @Override
    public native byte[] jobGetSavedData(long job);

    JniQpdfNative() {
    }
}
