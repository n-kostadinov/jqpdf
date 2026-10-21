package dev.machineminds.jqpdf.internal;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.machineminds.jqpdf.JobResult;
import dev.machineminds.jqpdf.QpdfJob;
import dev.machineminds.jqpdf.QpdfNative;
import dev.machineminds.jqpdf.QpdfUsageException;
import dev.machineminds.jqpdf.options.AdvancedControlOptions.PasswordMode;
import dev.machineminds.jqpdf.options.AttachmentOptions;
import dev.machineminds.jqpdf.options.EncryptionOptions.EncryptionConfig;
import dev.machineminds.jqpdf.options.GlobalOptions;
import dev.machineminds.jqpdf.options.JsonOptions.JsonStreamData;
import dev.machineminds.jqpdf.options.ModificationOptions;
import dev.machineminds.jqpdf.options.ModificationOptions.FlattenAnnotations;
import dev.machineminds.jqpdf.options.OverlayOptions;
import dev.machineminds.jqpdf.options.TransformationOptions.DecodeLevel;
import dev.machineminds.jqpdf.options.TransformationOptions.ObjectStreams;
import dev.machineminds.jqpdf.options.TransformationOptions.StreamData;
import dev.machineminds.jqpdf.options.TransformationOptions.UnreferencedResources;

/**
 * Mutable builder behind {@link QpdfJob}. Options accumulate in argv order;
 * {@link #run()} drives a fresh native job (create, initialize, run, capture,
 * destroy) so the builder can be reused and a failed initialization never
 * poisons the Java object.
 */
public final class QpdfJobImpl implements QpdfJob {

    private final QpdfNative qpdf;

    private String input;
    private boolean emptyInput;
    /** In-memory main input, spilled at run time; {@code null} when a path or stream is set. */
    private byte[] pendingInputContent;
    private String pendingInputSuffix;
    private InputStream pendingInputStream;
    private String output;
    private boolean outputToMemory;
    private boolean replaceInput;
    /** Option tokens: literal argv strings or {@link PendingSpill} markers resolved at run time. */
    private final List<Object> options = new ArrayList<>();
    private Path tempDir;

    /** Limits customized by any job in this process; see {@link #appendGlobalLimits}. */
    private static final java.util.Set<String> CUSTOMIZED_IN_PROCESS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private GlobalLimitsImpl globalLimits;

    public QpdfJobImpl(QpdfNative qpdf) {
        this.qpdf = qpdf;
    }

    @Override
    public QpdfJob emptyInput() {
        this.emptyInput = true;
        return this;
    }

    @Override
    public QpdfJob input(String file) {
        this.input = requireText(file, "input file");
        this.emptyInput = false;
        this.pendingInputContent = null;
        this.pendingInputSuffix = null;
        this.pendingInputStream = null;
        return this;
    }

    @Override
    public QpdfJob input(byte[] pdf) {
        if (pdf == null) {
            throw new QpdfUsageException("input PDF must not be null");
        }
        this.pendingInputContent = pdf;
        this.pendingInputSuffix = ".pdf";
        this.pendingInputStream = null;
        this.input = null;
        this.emptyInput = false;
        return this;
    }

    @Override
    public QpdfJob input(InputStream pdf) {
        if (pdf == null) {
            throw new QpdfUsageException("input PDF stream must not be null");
        }
        this.pendingInputStream = pdf;
        this.pendingInputContent = null;
        this.pendingInputSuffix = null;
        this.input = null;
        this.emptyInput = false;
        return this;
    }

    @Override
    public QpdfJob input(Path file) {
        if (file == null) {
            throw new QpdfUsageException("input file must not be null");
        }
        return input(file.toString());
    }

    @Override
    public QpdfJob output(String file) {
        this.output = requireText(file, "output file");
        this.replaceInput = false;
        this.outputToMemory = false;
        return this;
    }

    @Override
    public QpdfJob outputMemory() {
        this.outputToMemory = true;
        this.output = null;
        this.replaceInput = false;
        return this;
    }

    @Override
    public QpdfJob tempDir(Path dir) {
        if (dir == null) {
            throw new QpdfUsageException("temp directory must not be null");
        }
        this.tempDir = dir;
        return this;
    }

    @Override
    public QpdfJob replaceInput() {
        this.replaceInput = true;
        this.output = null;
        this.outputToMemory = false;
        return this;
    }

    @Override
    public QpdfJob jobJsonFile(String file) {
        options.add("--job-json-file=" + requireText(file, "job JSON file"));
        return this;
    }

    @Override
    public QpdfJob warningExit0() {
        options.add("--warning-exit-0");
        return this;
    }

    @Override
    public QpdfJob password(String password) {
        if (password == null) {
            throw new QpdfUsageException("password must not be null");
        }
        options.add("--password=" + password);
        return this;
    }

    @Override
    public QpdfJob passwordFile(String file) {
        options.add("--password-file=" + requireText(file, "password file"));
        return this;
    }

    @Override
    public QpdfJob verbose() {
        options.add("--verbose");
        return this;
    }

    @Override
    public QpdfJob progress() {
        options.add("--progress");
        return this;
    }

    @Override
    public QpdfJob noWarn() {
        options.add("--no-warn");
        return this;
    }

    @Override
    public QpdfJob deterministicId() {
        options.add("--deterministic-id");
        return this;
    }

    @Override
    public QpdfJob allowWeakCrypto() {
        options.add("--allow-weak-crypto");
        return this;
    }

    @Override
    public QpdfJob keepFilesOpen(boolean keepOpen) {
        options.add("--keep-files-open=" + (keepOpen ? "y" : "n"));
        return this;
    }

    @Override
    public QpdfJob keepFilesOpenThreshold(int count) {
        if (count < 1) {
            throw new QpdfUsageException("keep-files-open threshold must be positive, got " + count);
        }
        options.add("--keep-files-open-threshold=" + count);
        return this;
    }

    @Override
    public QpdfJob passwordIsHexKey() {
        options.add("--password-is-hex-key");
        return this;
    }

    @Override
    public QpdfJob suppressPasswordRecovery() {
        options.add("--suppress-password-recovery");
        return this;
    }

    @Override
    public QpdfJob passwordMode(PasswordMode mode) {
        options.add("--password-mode=" + mode.cliValue());
        return this;
    }

    @Override
    public QpdfJob suppressRecovery() {
        options.add("--suppress-recovery");
        return this;
    }

    @Override
    public QpdfJob ignoreXrefStreams() {
        options.add("--ignore-xref-streams");
        return this;
    }

    @Override
    public QpdfJob qdf() {
        options.add("--qdf");
        return this;
    }

    @Override
    public QpdfJob noOriginalObjectIds() {
        options.add("--no-original-object-ids");
        return this;
    }

    @Override
    public QpdfJob compressStreams(boolean compress) {
        options.add("--compress-streams=" + (compress ? "y" : "n"));
        return this;
    }

    @Override
    public QpdfJob decodeLevel(DecodeLevel level) {
        options.add("--decode-level=" + level.cliValue());
        return this;
    }

    @Override
    public QpdfJob streamData(StreamData mode) {
        options.add("--stream-data=" + mode.cliValue());
        return this;
    }

    @Override
    public QpdfJob recompressFlate() {
        options.add("--recompress-flate");
        return this;
    }

    @Override
    public QpdfJob jpegQuality(int quality) {
        if (quality < 0 || quality > 100) {
            throw new QpdfUsageException("jpeg quality must be between 0 and 100, got " + quality);
        }
        options.add("--jpeg-quality=" + quality);
        return this;
    }

    @Override
    public QpdfJob normalizeContent(boolean normalize) {
        options.add("--normalize-content=" + (normalize ? "y" : "n"));
        return this;
    }

    @Override
    public QpdfJob preserveUnreferenced() {
        options.add("--preserve-unreferenced");
        return this;
    }

    @Override
    public QpdfJob removeUnreferencedResources(UnreferencedResources mode) {
        options.add("--remove-unreferenced-resources=" + mode.cliValue());
        return this;
    }

    @Override
    public QpdfJob newlineBeforeEndstream() {
        options.add("--newline-before-endstream");
        return this;
    }

    @Override
    public QpdfJob coalesceContents() {
        options.add("--coalesce-contents");
        return this;
    }

    @Override
    public QpdfJob externalizeInlineImages() {
        options.add("--externalize-inline-images");
        return this;
    }

    @Override
    public QpdfJob iiMinBytes(long bytes) {
        if (bytes < 0) {
            throw new QpdfUsageException("ii-min-bytes must not be negative, got " + bytes);
        }
        options.add("--ii-min-bytes=" + bytes);
        return this;
    }

    @Override
    public QpdfJob minVersion(String version) {
        options.add("--min-version=" + requireText(version, "version"));
        return this;
    }

    @Override
    public QpdfJob forceVersion(String version) {
        options.add("--force-version=" + requireText(version, "version"));
        return this;
    }

    @Override
    public QpdfJob encrypt(java.util.function.Consumer<EncryptionConfig> config) {
        EncryptionConfigImpl enc = new EncryptionConfigImpl();
        config.accept(enc);
        options.add("--encrypt");
        options.addAll(enc.argvTokens());
        options.add("--");
        return this;
    }

    @Override
    public QpdfJob decrypt() {
        options.add("--decrypt");
        return this;
    }

    @Override
    public QpdfJob removeRestrictions() {
        options.add("--remove-restrictions");
        return this;
    }

    @Override
    public QpdfJob copyEncryption(String file) {
        options.add("--copy-encryption=" + requireText(file, "copy-encryption file"));
        return this;
    }

    @Override
    public QpdfJob encryptionFilePassword(String password) {
        if (password == null) {
            throw new QpdfUsageException("encryption file password must not be null");
        }
        options.add("--encryption-file-password=" + password);
        return this;
    }

    @Override
    public QpdfJob pages(java.util.function.Consumer<ModificationOptions.PagesGroup> specs) {
        PageGroupImpl group = new PageGroupImpl();
        specs.accept(group);
        options.add("--pages");
        options.addAll(group.argvTokens());
        options.add("--");
        return this;
    }

    @Override
    public QpdfJob collate() {
        options.add("--collate");
        return this;
    }

    @Override
    public QpdfJob collate(String groupSizes) {
        options.add("--collate=" + requireText(groupSizes, "collate group sizes"));
        return this;
    }

    @Override
    public QpdfJob splitPages() {
        options.add("--split-pages");
        return this;
    }

    @Override
    public QpdfJob splitPages(int groupSize) {
        if (groupSize < 1) {
            throw new QpdfUsageException("split-pages group size must be positive, got " + groupSize);
        }
        options.add("--split-pages=" + groupSize);
        return this;
    }

    @Override
    public QpdfJob flattenRotation() {
        options.add("--flatten-rotation");
        return this;
    }

    @Override
    public QpdfJob flattenAnnotations(FlattenAnnotations scope) {
        options.add("--flatten-annotations=" + scope.cliValue());
        return this;
    }

    @Override
    public QpdfJob generateAppearances() {
        options.add("--generate-appearances");
        return this;
    }

    @Override
    public QpdfJob optimizeImages() {
        options.add("--optimize-images");
        return this;
    }

    @Override
    public QpdfJob oiMinWidth(int width) {
        options.add("--oi-min-width=" + nonNegative(width, "oi-min-width"));
        return this;
    }

    @Override
    public QpdfJob oiMinHeight(int height) {
        options.add("--oi-min-height=" + nonNegative(height, "oi-min-height"));
        return this;
    }

    @Override
    public QpdfJob oiMinArea(long area) {
        if (area < 0) {
            throw new QpdfUsageException("oi-min-area must not be negative, got " + area);
        }
        options.add("--oi-min-area=" + area);
        return this;
    }

    @Override
    public QpdfJob keepInlineImages() {
        options.add("--keep-inline-images");
        return this;
    }

    @Override
    public QpdfJob removeAcroform() {
        options.add("--remove-acroform");
        return this;
    }

    @Override
    public QpdfJob removeInfo() {
        options.add("--remove-info");
        return this;
    }

    @Override
    public QpdfJob removeMetadata() {
        options.add("--remove-metadata");
        return this;
    }

    @Override
    public QpdfJob removePageLabels() {
        options.add("--remove-page-labels");
        return this;
    }

    @Override
    public QpdfJob removeStructure() {
        options.add("--remove-structure");
        return this;
    }

    @Override
    public QpdfJob overlay(java.util.function.Consumer<OverlayOptions.OverlayConfig> config) {
        return overlayGroup("--overlay", config);
    }

    @Override
    public QpdfJob underlay(java.util.function.Consumer<OverlayOptions.OverlayConfig> config) {
        return overlayGroup("--underlay", config);
    }

    private QpdfJob overlayGroup(String flag, java.util.function.Consumer<OverlayOptions.OverlayConfig> config) {
        PageGroupImpl group = new PageGroupImpl();
        config.accept(group);
        options.add(flag);
        options.addAll(group.argvTokens());
        options.add("--");
        return this;
    }

    @Override
    public QpdfJob addAttachment(java.util.function.Consumer<AttachmentOptions.AttachmentConfig> config) {
        AttachmentConfigImpl att = new AttachmentConfigImpl();
        config.accept(att);
        options.add("--add-attachment");
        options.addAll(att.argvTokens());
        options.add("--");
        return this;
    }

    @Override
    public QpdfJob copyAttachmentsFrom(
            java.util.function.Consumer<AttachmentOptions.CopyAttachmentsConfig> config) {
        AttachmentConfigImpl.CopyImpl copy = new AttachmentConfigImpl.CopyImpl();
        config.accept(copy);
        options.add("--copy-attachments-from");
        options.addAll(copy.argvTokens());
        options.add("--");
        return this;
    }

    @Override
    public QpdfJob removeAttachment(String key) {
        options.add("--remove-attachment=" + requireText(key, "attachment key"));
        return this;
    }

    @Override
    public QpdfJob listAttachments() {
        options.add("--list-attachments");
        return this;
    }

    @Override
    public QpdfJob showAttachment(String key) {
        options.add("--show-attachment=" + requireText(key, "attachment key"));
        return this;
    }

    @Override
    public QpdfJob isEncrypted() {
        options.add("--is-encrypted");
        return this;
    }

    @Override
    public QpdfJob requiresPassword() {
        options.add("--requires-password");
        return this;
    }

    @Override
    public QpdfJob check() {
        options.add("--check");
        return this;
    }

    @Override
    public QpdfJob showEncryption() {
        options.add("--show-encryption");
        return this;
    }

    @Override
    public QpdfJob showEncryptionKey() {
        options.add("--show-encryption-key");
        return this;
    }

    @Override
    public QpdfJob checkLinearization() {
        options.add("--check-linearization");
        return this;
    }

    @Override
    public QpdfJob showLinearization() {
        options.add("--show-linearization");
        return this;
    }

    @Override
    public QpdfJob showXref() {
        options.add("--show-xref");
        return this;
    }

    @Override
    public QpdfJob showObject(String objectSpec) {
        options.add("--show-object=" + requireText(objectSpec, "object spec"));
        return this;
    }

    @Override
    public QpdfJob rawStreamData() {
        options.add("--raw-stream-data");
        return this;
    }

    @Override
    public QpdfJob filteredStreamData() {
        options.add("--filtered-stream-data");
        return this;
    }

    @Override
    public QpdfJob showNpages() {
        options.add("--show-npages");
        return this;
    }

    @Override
    public QpdfJob showPages() {
        options.add("--show-pages");
        return this;
    }

    @Override
    public QpdfJob withImages() {
        options.add("--with-images");
        return this;
    }

    @Override
    public QpdfJob json() {
        options.add("--json");
        return this;
    }

    @Override
    public QpdfJob json(int version) {
        if (version != 1 && version != 2) {
            throw new QpdfUsageException("json version must be 1 or 2, got " + version);
        }
        options.add("--json=" + version);
        return this;
    }

    @Override
    public QpdfJob jsonKey(String key) {
        options.add("--json-key=" + requireText(key, "json key"));
        return this;
    }

    @Override
    public QpdfJob jsonObject(String objectSpec) {
        options.add("--json-object=" + requireText(objectSpec, "json object spec"));
        return this;
    }

    @Override
    public QpdfJob jsonStreamData(JsonStreamData mode) {
        options.add("--json-stream-data=" + mode.cliValue());
        return this;
    }

    @Override
    public QpdfJob jsonStreamPrefix(String prefix) {
        options.add("--json-stream-prefix=" + requireText(prefix, "json stream prefix"));
        return this;
    }

    @Override
    public QpdfJob jsonOutput() {
        options.add("--json-output");
        return this;
    }

    @Override
    public QpdfJob jsonOutput(int version) {
        if (version != 2) {
            throw new QpdfUsageException(
                    "json-output version must be 2 (the only version with faithful-serialization"
                            + " presets; use jsonOutput() for the latest), got " + version);
        }
        options.add("--json-output=" + version);
        return this;
    }

    @Override
    public QpdfJob jsonInput() {
        options.add("--json-input");
        return this;
    }

    @Override
    public QpdfJob jsonInputContent(String json) {
        return jsonInputContent(requireText(json, "json input").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public QpdfJob jsonInputContent(byte[] json) {
        if (json == null) {
            throw new QpdfUsageException("json input must not be null");
        }
        options.add("--json-input");
        this.pendingInputContent = json;
        this.pendingInputSuffix = ".json";
        this.pendingInputStream = null;
        this.input = null;
        this.emptyInput = false;
        return this;
    }

    @Override
    public QpdfJob updateFromJson(String file) {
        options.add("--update-from-json=" + requireText(file, "qpdf JSON file"));
        return this;
    }

    @Override
    public QpdfJob updateFromJsonContent(String json) {
        return updateFromJsonContent(requireText(json, "qpdf JSON").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public QpdfJob updateFromJsonContent(byte[] json) {
        if (json == null) {
            throw new QpdfUsageException("qpdf JSON must not be null");
        }
        options.add(new PendingSpill(json, "--update-from-json=", ".json"));
        return this;
    }

    @Override
    public QpdfJob global(java.util.function.Consumer<GlobalOptions.GlobalLimits> limits) {
        if (globalLimits == null) {
            globalLimits = new GlobalLimitsImpl();
        }
        limits.accept(globalLimits);
        return this;
    }

    /**
     * qpdf keeps the {@code --global} limits in process-wide statics: a limit
     * customized by one job silently applies to every later job in the same
     * process. Against the real adapter, every limit that has ever been
     * customized in this process is pinned to this job's effective value
     * (qpdf's default unless the job overrides it); with
     * {@code noDefaultLimits}, all optional limits are written explicitly.
     * Stateless adapters get the plain user tokens.
     */
    private void appendGlobalLimits(List<String> argv) {
        if (globalLimits == null && !qpdf.sharesProcessGlobalState()) {
            return;
        }
        if (!qpdf.sharesProcessGlobalState()) {
            if (globalLimits.values.isEmpty() && !globalLimits.noDefaultLimits) {
                return;
            }
            argv.add("--global");
            if (globalLimits.noDefaultLimits) {
                argv.add("--no-default-limits");
            }
            globalLimits.values.forEach((flag, value) -> argv.add(flag + "=" + value));
            argv.add("--");
            return;
        }
        boolean unlimitedOptional = globalLimits != null && globalLimits.noDefaultLimits;
        Map<String, String> effective = new java.util.LinkedHashMap<>();
        if (globalLimits != null) {
            effective.putAll(globalLimits.values);
        }
        for (String flag : GlobalLimitsImpl.QPDF_DEFAULTS.keySet()) {
            if (effective.containsKey(flag)) {
                continue;
            }
            boolean optional = !"--parser-max-nesting".equals(flag);
            if (CUSTOMIZED_IN_PROCESS.contains(flag) || (unlimitedOptional && optional)) {
                effective.put(flag, unlimitedOptional ? unlimitedValue(flag)
                        : GlobalLimitsImpl.QPDF_DEFAULTS.get(flag));
            }
        }
        for (Map.Entry<String, String> entry : effective.entrySet()) {
            if (!entry.getValue().equals(GlobalLimitsImpl.QPDF_DEFAULTS.get(entry.getKey()))) {
                CUSTOMIZED_IN_PROCESS.add(entry.getKey());
            }
        }
        if (effective.isEmpty() && !unlimitedOptional) {
            return;
        }
        argv.add("--global");
        if (unlimitedOptional) {
            argv.add("--no-default-limits");
        }
        effective.forEach((flag, value) -> argv.add(flag + "=" + value));
        argv.add("--");
    }

    private static String unlimitedValue(String flag) {
        return "--parser-max-errors".equals(flag) ? "0" : "4294967295";
    }

    @Override
    public QpdfJob staticId() {
        options.add("--static-id");
        return this;
    }

    @Override
    public QpdfJob staticAesIv() {
        options.add("--static-aes-iv");
        return this;
    }

    @Override
    public QpdfJob linearizePass1(String file) {
        options.add("--linearize-pass1=" + requireText(file, "linearize pass 1 file"));
        return this;
    }

    @Override
    public QpdfJob testJsonSchema() {
        options.add("--test-json-schema");
        return this;
    }

    @Override
    public QpdfJob reportMemoryUsage() {
        options.add("--report-memory-usage");
        return this;
    }

    private static String nonNegative(int value, String what) {
        if (value < 0) {
            throw new QpdfUsageException(what + " must not be negative, got " + value);
        }
        return Integer.toString(value);
    }

    @Override
    public QpdfJob linearize() {
        options.add("--linearize");
        return this;
    }

    @Override
    public QpdfJob compressionLevel(int level) {
        if (level < 1 || level > 9) {
            throw new QpdfUsageException("compression level must be between 1 and 9, got " + level);
        }
        options.add("--compression-level=" + level);
        return this;
    }

    @Override
    public QpdfJob objectStreams(ObjectStreams mode) {
        options.add("--object-streams=" + mode.cliValue());
        return this;
    }

    @Override
    public QpdfJob rotate(String spec) {
        options.add("--rotate=" + requireText(spec, "rotate spec"));
        return this;
    }

    @Override
    public QpdfJob setPageLabels(String... specs) {
        if (specs.length == 0) {
            throw new QpdfUsageException("setPageLabels requires at least one label spec");
        }
        options.add("--set-page-labels");
        for (String spec : specs) {
            options.add(requireText(spec, "page label spec"));
        }
        options.add("--");
        return this;
    }

    @Override
    public synchronized JobResult run() {
        boolean spilledMainInput = pendingInputContent != null || pendingInputStream != null;
        if (input == null && !emptyInput && !spilledMainInput) {
            throw new QpdfUsageException("no input file configured; use input(...) or emptyInput()");
        }
        if (replaceInput && spilledMainInput) {
            throw new QpdfUsageException(
                    "replaceInput() cannot be combined with input(byte[]) or input(InputStream)");
        }
        SpillFiles spills = SpillFiles.forJob(tempDir);
        try {
            List<String> argv = synthesizeArgv(spills);
            long job = qpdf.jobCreate();
            try {
                int init = qpdf.jobInitializeFromArgv(job, ArgvCodec.encode(argv), argv.size());
                return JobResult.capture(qpdf, job, init == 0 ? qpdf.jobRun(job) : init);
            } finally {
                qpdf.jobDestroy(job);
            }
        } finally {
            spills.cleanup();
        }
    }

    @Override
    public synchronized JobResult runFromJson(String jobJson) {
        byte[] json = requireText(jobJson, "job JSON").getBytes(StandardCharsets.UTF_8);
        long job = qpdf.jobCreate();
        try {
            int init = qpdf.jobInitializeFromJson(job, json);
            return JobResult.capture(qpdf, job, init == 0 ? qpdf.jobRun(job) : init);
        } finally {
            qpdf.jobDestroy(job);
        }
    }

    /**
     * The argv handed to the native library: program name, input file,
     * options in call order, output file last — a valid CLI ordering for
     * every combination (spec §2). In-memory inputs and content options are
     * spilled here, inside the run's {@link SpillFiles} lifecycle.
     */
    private List<String> synthesizeArgv(SpillFiles spills) {
        List<String> argv = new ArrayList<>();
        argv.add("qpdf");
        argv.add(emptyInput ? "--empty" : resolveInputFile(spills));
        for (Object token : options) {
            if (token instanceof PendingSpill) {
                argv.add(((PendingSpill) token).render(spills));
            } else {
                argv.add((String) token);
            }
        }
        appendGlobalLimits(argv);
        if (replaceInput) {
            argv.add("--replace-input");
        } else if (output != null) {
            argv.add(output);
        } else if (outputToMemory) {
            argv.add("-");
        }
        return argv;
    }

    private String resolveInputFile(SpillFiles spills) {
        if (input != null) {
            return input;
        }
        if (pendingInputContent != null) {
            return spills.spill(pendingInputContent, pendingInputSuffix).toString();
        }
        if (pendingInputStream != null) {
            return spills.spill(pendingInputStream, ".pdf").toString();
        }
        throw new QpdfUsageException("no input file configured; use input(...) or emptyInput()");
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new QpdfUsageException(what + " must not be blank");
        }
        return value;
    }
}
