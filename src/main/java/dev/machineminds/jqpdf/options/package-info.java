/**
 * Fluent option builders for {@link dev.machineminds.jqpdf.QpdfJob}, one
 * interface per functional area of the qpdf CLI: general invocation, advanced
 * control, transformation, modification, encryption, overlay/underlay,
 * attachments, inspection, JSON, and global limits. {@code QpdfJob} extends
 * them all, so every option is reachable directly on the job and each method
 * returns the job to chain.
 *
 * <p>Options are recorded in call order, matching the CLI's
 * later-options-override-earlier-ones semantics. Methods name the CLI flag
 * they map to (e.g. {@code --linearize}); grouped CLI options such as
 * {@code --encrypt} take a configuration consumer for their sub-options.
 *
 * <p>References of the form {@code spec §x.y} follow the convention
 * described in {@link dev.machineminds.jqpdf}.
 */
package dev.machineminds.jqpdf.options;
