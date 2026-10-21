# Mocking

Job logic without native libraries. The public API is interface-based —
[`QpdfJob`](../../src/main/java/dev/machineminds/jqpdf/QpdfJob.java) is an
interface and `Jqpdf.createJob(QpdfNative)` accepts any
[`QpdfNative`](../../src/main/java/dev/machineminds/jqpdf/QpdfNative.java)
adapter, so tests drive the builder against a fake and never touch the
bundled binaries. The project's own argv-synthesis tests do exactly this.

## A minimal recording fake

`QpdfNative` is small enough to fake by hand: record the argv blob each
job was initialized with, and answer every run with success. argv travels
as one blob of NUL-terminated UTF-8 strings, `argv[0]` an ignored program
name:

```java
final class RecordingFake implements QpdfNative {
    final List<String> argv = new ArrayList<>();

    @Override public int nativeAbiVersion() { return EXPECTED_ABI; }
    @Override public long jobCreate() { return 1; }
    @Override public void jobDestroy(long job) { }

    @Override
    public int jobInitializeFromArgv(long job, byte[] argvUtf8, int argc) {
        argv.addAll(Arrays.asList(
                new String(argvUtf8, StandardCharsets.UTF_8).split("\0")));
        return 0;                                   // initialize succeeded
    }

    @Override public int jobInitializeFromJson(long job, byte[] jsonUtf8) {
        throw new UnsupportedOperationException("not used in these tests");
    }
    @Override public int jobRun(long job) { return 0; }   // exit code 0

    @Override public byte[] jobGetInfoText(long job)  { return new byte[0]; }
    @Override public byte[] jobGetWarnText(long job)  { return new byte[0]; }
    @Override public byte[] jobGetErrorText(long job) { return new byte[0]; }
    @Override public byte[] jobGetSavedData(long job) { return new byte[0]; }

    @Override public boolean sharesProcessGlobalState() { return false; }
}
```

## Asserting what the builder produces

Every option method only synthesizes CLI tokens; a fake lets you assert
them exactly, the way qpdf's own argv would see them:

```java
RecordingFake fake = new RecordingFake();

Jqpdf.createJob(fake)
        .input("in.pdf").output("out.pdf")
        .rotate("+90:2,4,6").rotate("+180:7-8")
        .run();

assertEquals(
        List.of("in.pdf", "--rotate=+90:2,4,6", "--rotate=+180:7-8", "out.pdf"),
        fake.argv.subList(1, fake.argv.size()));     // argv[0] is the program name
```

Builder misuse never reaches the native layer — `QpdfUsageException` is
thrown before any adapter call, which the fake can prove:

```java
assertThrows(QpdfUsageException.class,
        () -> Jqpdf.createJob(fake).output("out.pdf").run());   // input missing
assertEquals(0, fake.argv.size());
```

`runFromJson(jobJson)` bypasses argv entirely and hands the JSON document
to `jobInitializeFromJson` verbatim, so JSON-passthrough assertions go
there (see the [JSON guide](json.md)).

## Replaying outcomes

A fake that returns configurable exit codes and captured text drives the
error and warning paths of the result contract without a real PDF:

```java
fake.runExitCode = 2;
fake.errorText = "file not found";

JobResult result = Jqpdf.createJob(fake)
        .input("missing.pdf").output("out.pdf").run();

assertEquals(2, result.exitCode());
assertEquals("file not found", result.errorText());
assertThrows(QpdfJobException.class, result::orThrow);
```

Add writable fields for the exit code and the three text streams, and
this generalizes to warnings (exit 3), info text, and saved data — the
shapes a [`JobResult`](job-result.md) can expose.

## Fakes declare no process-global state

qpdf keeps its `--global` parser limits in process-wide statics; the
default adapter reports `sharesProcessGlobalState() == true`, which makes
the job builder pin those limits per run so jobs stay independent. Fakes
with no native state return `false` and simply see only their explicitly
configured options in argv. Why that pinning exists is covered in
[global limits and testing](global-limits.md).

## The full-size example

The project's test tree ships a complete version of this pattern:
[`FakeQpdfNative`](../../src/test/java/dev/machineminds/jqpdf/test/FakeQpdfNative.java)
records every run (argv and JSON separately, `argvOfRun(n)` strips the
program name), replays configurable exit codes and output, and exposes an
`onInitialize` observer invoked while spill files still exist. The
[argv-synthesis tests](../../src/test/java/dev/machineminds/jqpdf/QpdfJobArgvTest.java)
show it asserting every option shape.
