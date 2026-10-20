package dev.machineminds.jqpdf.test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.machineminds.jqpdf.QpdfNative;

/**
 * Recording fake for the {@link QpdfNative} port: captures the argv (or JSON)
 * each job was initialized with and replays configurable exit codes and
 * captured output. No native library involved.
 */
public final class FakeQpdfNative implements QpdfNative {

    private final List<List<String>> argvRuns = new ArrayList<>();
    private final List<String> jsonRuns = new ArrayList<>();

    public int initializeExitCode = 0;
    public int runExitCode = 0;
    public String infoText = "";
    public String warnText = "";
    public String errorText = "";
    public byte[] savedData = new byte[0];

    /** The argv (without the leading program name) of the n-th run. */
    public List<String> argvOfRun(int index) {
        return argvRuns.get(index).subList(1, argvRuns.get(index).size());
    }

    public int runCount() {
        return argvRuns.size();
    }

    public List<String> jsonRuns() {
        return jsonRuns;
    }

    @Override
    public int nativeAbiVersion() {
        return EXPECTED_ABI;
    }

    @Override
    public long jobCreate() {
        return argvRuns.size() + 1;
    }

    @Override
    public void jobDestroy(long job) {
        // nothing to free
    }

    @Override
    public int jobInitializeFromArgv(long job, byte[] argvUtf8, int argc) {
        argvRuns.add(dev.machineminds.jqpdf.internal.ArgvCodec.decode(argvUtf8));
        return initializeExitCode;
    }

    @Override
    public int jobInitializeFromJson(long job, byte[] jsonUtf8) {
        jsonRuns.add(new String(jsonUtf8, StandardCharsets.UTF_8));
        return initializeExitCode;
    }

    @Override
    public int jobRun(long job) {
        return runExitCode;
    }

    @Override
    public byte[] jobGetInfoText(long job) {
        return infoText.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] jobGetWarnText(long job) {
        return warnText.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] jobGetErrorText(long job) {
        return errorText.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] jobGetSavedData(long job) {
        return savedData;
    }

    @Override
    public boolean sharesProcessGlobalState() {
        return false;
    }
}
