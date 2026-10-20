package dev.machineminds.jqpdf;

import dev.machineminds.jqpdf.options.AdvancedControlOptions;
import dev.machineminds.jqpdf.options.AttachmentOptions;
import dev.machineminds.jqpdf.options.EncryptionOptions;
import dev.machineminds.jqpdf.options.GlobalOptions;
import dev.machineminds.jqpdf.options.InspectionOptions;
import dev.machineminds.jqpdf.options.JsonOptions;
import dev.machineminds.jqpdf.options.GeneralOptions;
import dev.machineminds.jqpdf.options.ModificationOptions;
import dev.machineminds.jqpdf.options.OverlayOptions;
import dev.machineminds.jqpdf.options.TransformationOptions;

/**
 * One qpdf job: a fluent, reusable configuration plus the way to run it.
 * Create with {@link Jqpdf#createJob()} (bundled binaries) or
 * {@link Jqpdf#createJob(QpdfNative)} (substitute adapter, e.g. a test
 * fake).
 *
 * <p>The interface extends one options interface per qpdf functional area;
 * every option method returns {@code QpdfJob} so calls chain freely. Options
 * are recorded in call order, matching the CLI's "later options override
 * earlier ones" semantics.
 *
 * <p>Concurrency: a single job may not be configured or run from two threads
 * at once (qpdf jobs are single-threaded objects); separate jobs run in
 * parallel without coordination.
 */
public interface QpdfJob extends GeneralOptions, AdvancedControlOptions, TransformationOptions,
        ModificationOptions, EncryptionOptions, OverlayOptions, AttachmentOptions,
        InspectionOptions, JsonOptions, GlobalOptions {

    /** The input file (CLI {@code infile}). */
    QpdfJob input(String file);

    /** The output file (CLI {@code outfile}); omitted by inspection jobs. */
    QpdfJob output(String file);

    /**
     * Runs the job: synthesizes argv, configures and runs a fresh native
     * qpdf job, and returns its outcome. The builder stays intact, so a job
     * can be re-configured and run again.
     */
    JobResult run();

    /**
     * Runs the job described by a QPDFJob JSON document (spec §7) as-is,
     * ignoring the builder's options. Same result contract as {@link #run()}.
     */
    JobResult runFromJson(String jobJson);
}
