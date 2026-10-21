# qpdf feature coverage

Audit of every qpdf 12.4.2 CLI feature (per `docs/qpdf-spec.md`, 140 options in
§5.1–§5.15) against the jqpdf Java API and its tests. This matrix is the
authoritative answer to "is every qpdf feature accessible from Java, and is it
verified against real PDFs?".

Legend:

- **Argv test** — a `FakeQpdfNative` test asserting the builder emits the exact
  CLI tokens (token-level regression net). Test names are in the class of the
  same area unless prefixed with another class.
- **Real-PDF test** — a test that runs the bundled native qpdf on a fixture
  from the sibling `../qpdf` checkout (or an artifact derived through jqpdf
  itself) and asserts an observable outcome: output PDF content read back
  through jqpdf's inspection/JSON options, captured text/binary streams, exit
  codes, or byte-level comparison.
- **N/A** — deliberately out of scope, with the reason.

Verification conventions for real-PDF tests: outputs are verified through
jqpdf itself (self-verification: `show-object`, `show-npages`, `show-pages`,
`json`, exit codes, `savedData()`), plus direct byte/text checks on written
files. Tests skip with a message when the qpdf checkout or the native stack of
the running platform is absent (`Fixtures.assumePdf` / `nativeAvailable`).

## Invocation model (spec §2)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| positional `infile` | `input(String)` | `plainCopyPlacesInputBeforeOutput` | throughout | |
| positional `outfile` | `output(String)` | `plainCopyPlacesInputBeforeOutput` | throughout | |
| `outfile` = `-` (stdout) | `output("-")` | — | `stdoutOutputDeliversPdfBytesThroughTheSavePipeline` | argv passes `-` through verbatim |
| `--empty` | `emptyInput()` | `emptyInputReplacesTheInputSlot` | `emptyInputWithPagesBuildsFreshDocument` | |
| `--replace-input` | `replaceInput()` | `replaceInputOccupiesTheOutputSlot`, `laterCallDecidesBetweenOutputAndReplaceInput` | `replaceInputOverwritesTheFile` | |
| `--job-json-file` | `jobJsonFile(String)` | `generalOptionsMapToTheirCliTokens` | `jobJsonFileMergesWithBuilderOptions` | |
| job JSON passthrough | `runFromJson(String)` | `jsonPassthroughReachesInitializeFromJsonVerbatim` | `runFromJsonCopiesPdfEndToEnd` | |
| exit-code surface (0/2/3, §3) | `JobResult.exitCode()` | `runFailureCarriesExitCodeAndStreamsInResult` | `builderReportsNativeUsageErrorsAsExitCode2`, `isEncryptedDistinguishesEncryptedFiles`, `requiresPasswordRecognizesTheCorrectPassword` | |
| output capture (info/warn/error/save) | `JobResult` | `runFailureCarriesExitCodeAndStreamsInResult` | `JobCaptureTest` | |
| one job per thread | — | — | `QpdfConcurrencyTest` | |

## §5.2 Exit status (1)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--warning-exit-0` | `warningExit0()` | ✓ | `warningExit0TurnsWarningsIntoSuccess` | |

## §5.3 Shell completion (2) — N/A

`--completion-bash`, `--completion-zsh`: CLI-only, emitted by qpdf's argument
parser; not meaningful for a library (spec §5.3, `docs/guides/info-options.md`).

## §5.4 Help/information (6+1) — CLI-only

| Feature | Java API | Real-PDF test | Notes |
|---|---|---|---|
| `--version` | `Jqpdf.qpdfVersion()` | `JqpdfInfoTest` | derived from the pinned `qpdf.version` |
| `--copyright` | `Jqpdf.qpdfCopyright()` | `JqpdfInfoTest` | |
| `--help` | — | N/A | this documentation set is the equivalent |
| `--show-crypto` | — | N/A | job C API exposes nothing; build-time dependent (`info-options.md`) |
| `--job-json-help` | — | N/A | same |
| `--json-help` | — | N/A | same |
| `--zopfli` | — | N/A | status report only; activation is the `QPDF_ZOPFLI` env var |

## §5.5 General options (9)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--password` | `password(String)` | ✓ | `passwordOpensEncryptedFixture` | `20-pages.pdf`, password `user` |
| `--password-file` | `passwordFile(String)` | ✓ | `passwordFileOpensEncryptedFixture` | |
| `--verbose` | `verbose()` | ✓ | `verboseReportsTheWrittenFile` | |
| `--progress` | `progress()` | ✓ | `progressReportsWritePercentageInInfoText` | |
| `--no-warn` | `noWarn()` | ✓ | `noWarnSuppressesWarningTextButKeepsExitCode` | |
| `--deterministic-id` | `deterministicId()` | ✓ | `deterministicIdConvergesToByteIdenticalOutput` | |
| `--allow-weak-crypto` | `allowWeakCrypto()` | ✓ | `weakCryptoIsRejectedUnlessAllowed` | |
| `--keep-files-open` | `keepFilesOpen(boolean)` | ✓ | `keepFilesOpenVariantsCompleteMultiFileMerge` | no output-visible effect; execution-verified by design |
| `--keep-files-open-threshold` | `keepFilesOpenThreshold(int)` | ✓ | `keepFilesOpenVariantsCompleteMultiFileMerge` | same |

## §5.6 Advanced control (5)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--password-is-hex-key` | `passwordIsHexKey()` | ✓ | `hexEncryptionKeyOpensTheFileWithoutAPassword` | |
| `--suppress-password-recovery` | `suppressPasswordRecovery()` | ✓ | `suppressPasswordRecoveryStillChecksTheExactPassword` | full recovery-path behavior needs a transcode-dependent fixture |
| `--password-mode` | `passwordMode(PasswordMode)` | ✓ | `passwordModeHexBytesOpensEncryptedFixture`, `passwordModeUnicodeRoundTripsNonAsciiPassword` | the `unicode` rejection branch is unreachable from Java: `String`→UTF-8 argv is always valid |
| `--suppress-recovery` | `suppressRecovery()` | ✓ | `suppressRecoveryTurnsDamageIntoAnError` | |
| `--ignore-xref-streams` | `ignoreXrefStreams()` | ✓ | `ignoreXrefStreamsFallsBackToTheClassicXrefTable` (fixture `hybrid-xref.pdf`; a pure xref-stream file has no fallback and is expected to fail) | |

## §5.7 PDF transformation (25)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--linearize` | `linearize()` | — | `linearizeProducesAFileWithLinearizationDictionary` | |
| `--encrypt … --` | `encrypt(Consumer)` | `encryptGroupMapsPasswordsBitsAndSubOptionsInCliOrder` | `aes256RoundTrip` | details §5.10 |
| `--decrypt` | `decrypt()` | ✓ | `aes256RoundTrip` | |
| `--remove-restrictions` | `removeRestrictions()` | — | `removeRestrictionsDisablesTheSignatures` (fixture `minimal-signed-restricted.pdf`; the option disables signatures) | |
| `--copy-encryption` | `copyEncryption(String)` | — | `copyEncryptionReusesAnotherFilesParameters` | |
| `--encryption-file-password` | `encryptionFilePassword(String)` | — | `encryptionFilePasswordUnlocksTheCopySource` | |
| `--qdf` | `qdf()` | ✓ | `qdfOutputCarriesTheQdfMarker` | |
| `--no-original-object-ids` | `noOriginalObjectIds()` | ✓ | `noOriginalObjectIdsOmitsTheQdfComments` | |
| `--compress-streams=n` | `compressStreams(boolean)` | ✓ | `compressStreamsFalseLeavesDecodedStreamsUncompressed` | |
| `--decode-level` | `decodeLevel(DecodeLevel)` | ✓ | `decodeLevelNoneSkipsFlateDecoding` | |
| `--stream-data` | `streamData(StreamData)` | ✓ | `streamDataModesMatchTheirDocumentedEquivalents` | |
| `--recompress-flate` | `recompressFlate()` | ✓ | `recompressFlateAppliesTheConfiguredCompressionLevel` | |
| `--compression-level` | `compressionLevel(int)` | ✓ + range validation | `recompressFlateAppliesTheConfiguredCompressionLevel` | |
| `--jpeg-quality` | `jpegQuality(int)` | ✓ + range validation | `jpegQualityScalesTheRecompressedImageSize` (sizes differ; ordering is image-dependent) | |
| `--normalize-content` | `normalizeContent(boolean)` | ✓ | `normalizeContentConvertsCarriageReturnsToUnixNewlines` (content built via the qpdf JSON round-trip) | fixture `c-normalized-content.pdf` |
| `--object-streams` | `objectStreams(ObjectStreams)` | — | `objectStreamsGenerateRaisesThePdfVersion`, `objectStreamsDisableWritesAllObjectsAtTopLevel` | |
| `--preserve-unreferenced` | `preserveUnreferenced()` | ✓ | `preserveUnreferencedKeepsOrphanedObjects` (fixture `unreferenced-objects.pdf`) | |
| `--remove-unreferenced-resources` | `removeUnreferencedResources(mode)` | ✓ | `removeUnreferencedResourcesPrunesSplitPageResources` (fixture `shared-images.pdf`) | |
| `--preserve-unreferenced-resources` | — | — | N/A | deprecated synonym; use `removeUnreferencedResources(NO)` |
| `--newline-before-endstream` | `newlineBeforeEndstream()` | ✓ | `newlineBeforeEndstreamSeparatesContentFromEndstream` | |
| `--coalesce-contents` | `coalesceContents()` | ✓ | `coalesceContentsJoinsEachPagesContentStreams` (fixture `coalesce.pdf`) | |
| `--externalize-inline-images` | `externalizeInlineImages()` | ✓ | `externalizeInlineImagesTurnsThemIntoImageObjects` (fixture `large-inline-image.pdf`) | |
| `--ii-min-bytes` | `iiMinBytes(long)` | ✓ + validation | `externalizeInlineImagesTurnsThemIntoImageObjects` | |
| `--min-version` | `minVersion(String)` | ✓ | `minVersionRaisesThePdfHeader` | |
| `--force-version` | `forceVersion(String)` | ✓ | `forceVersionOverridesTheHeader` | |

## §5.8 PDF modification (22)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--pages … --` (+`--file`, `--range`) | `pages(Consumer)` | `pagesGroupKeepsFileRangePasswordOrder` | `pagesSelectsAndMergesFromMultipleFiles` | |
| `--collate[=n[,m]]` | `collate()` / `collate(String)` | ✓ | `collateInterleavesPagesAcrossGroups` (page order proven via /Rotate markers) | |
| `--split-pages[=n]` | `splitPages()` / `splitPages(int)` | ✓ | `splitPagesWritesOneFilePerPage`, `splitPagesWithGroupSizeWritesCeilingFileCount` | |
| `--overlay` | `overlay(Consumer)` | ✓ | `overlayAppliesStampToOutput` | |
| `--underlay` | `underlay(Consumer)` | ✓ | `underlayDrawsBeneathOutput` | |
| `--flatten-rotation` | `flattenRotation()` | ✓ | `flattenRotationBakesRotationIntoTheContent` | |
| `--flatten-annotations` | `flattenAnnotations(scope)` | ✓ | `flattenAnnotationsPushesThemIntoThePageContent` (appearance XObject appears in the page resources) | |
| `--rotate` | `rotate(String)` | ✓ | `rotateSetsRelativeAndAbsoluteRotation` (page dictionaries via `--show-object`) | also exercised by `QpdfConcurrencyTest` |
| `--generate-appearances` | `generateAppearances()` | ✓ | `generateAppearancesClearsStaleAppearanceFlag` | |
| `--optimize-images` | `optimizeImages()` | ✓ | `optimizeImagesShrinksConvertibleImages` (fixture `image-streams.pdf`) | |
| `--oi-min-width` | `oiMinWidth(int)` | ✓ | `oiMinThresholdsSuppressImageConversion` | |
| `--oi-min-height` | `oiMinHeight(int)` | ✓ | `oiMinThresholdsSuppressImageConversion` | |
| `--oi-min-area` | `oiMinArea(long)` | ✓ | `oiMinThresholdsSuppressImageConversion` | |
| `--keep-inline-images` | `keepInlineImages()` | ✓ | `keepInlineImagesOptsOutOfImplicitExternalization` (fixture `large-inline-image.pdf`) | |
| `--remove-acroform` | `removeAcroform()` | ✓ | `removeAcroformDropsTheFormDictionary` (fixture `annotations-rotated-90.pdf`) | |
| `--remove-info` | `removeInfo()` | ✓ | `removeInfoKeepsOnlyTheModificationDate` (fixture `metadata-crypt-filter.pdf`) | |
| `--remove-metadata` | `removeMetadata()` | ✓ | `removeMetadataRemovesTheXmpStream` (fixture `metadata-crypt-filter.pdf` carries XMP) | |
| `--remove-page-labels` | `removePageLabels()` | ✓ | `removePageLabelsClearsTheLabelTree` | |
| `--remove-structure` | `removeStructure()` | ✓ | `removeStructureRemovesTheTreeAndMarkInfo` | |
| `--set-page-labels` | `setPageLabels(String...)` | ✓ | `setPageLabelsRunsOnLabeledFixture`, `setPageLabelsEmitsTheLabelsSummary` | |

## §5.9 Overlay/underlay group (3)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--to` | `OverlayConfig.to(String)` | `overlayGroupMapsToToFromRepeatTokens` | `toFromAndRepeatSelectTheStampedPages` | |
| `--from` | `OverlayConfig.from(String)` | ✓ | `toFromAndRepeatSelectTheStampedPages` (stamped pages detected by changed content) | |
| `--repeat` | `OverlayConfig.repeat(String)` | ✓ | `toFromAndRepeatSelectTheStampedPages` (incl. the empty-`from` case) | |

## §5.10 Encryption (16)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--user-password` | `EncryptionConfig.userPassword` | ✓ | `aes256RoundTrip` | |
| `--owner-password` | `EncryptionConfig.ownerPassword` | ✓ | `aes256RoundTrip` | |
| `--bits=40/128/256` | `bits40()/bits128()/bits256()` | ✓ | 256: `aes256RoundTrip`; 40: `weakCryptoIsRejectedUnlessAllowed`, `rc4_40RoundTripWithWeakCryptoAllowed`; 128: `aes128RoundTrip` | |
| `--accessibility` | `accessibility(boolean)` | — | `restrictionsAreRecordedInTheEncryptionDictionary` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--annotate` | `annotate(boolean)` | — | `granularRestrictionsMapToTheirOwnFlags` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--assemble` | `assemble(boolean)` | — | `granularRestrictionsMapToTheirOwnFlags`; argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--extract` | `extract(boolean)` | ✓ | `restrictionsAreRecordedInTheEncryptionDictionary` | |
| `--form` | `form(boolean)` | — | `granularRestrictionsMapToTheirOwnFlags`; argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--modify-other` | `modifyOther(boolean)` | — | `granularRestrictionsMapToTheirOwnFlags`; argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--modify` | `modify(Modify)` | ✓ | `restrictionsAreRecordedInTheEncryptionDictionary` | |
| `--print` | `print(Print)` | ✓ | `restrictionsAreRecordedInTheEncryptionDictionary` | |
| `--cleartext-metadata` | `cleartextMetadata()` | ✓ | `cleartextMetadataStoresXmpUnencrypted` (XMP stays readable in the raw file) | |
| `--use-aes` | `useAes(boolean)` | — | `aes128RoundTrip` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--allow-insecure` | `allowInsecure()` | — | `allowInsecurePermitsTheEmptyOwnerPassword` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--force-V4` | `forceV4()` | — | `forceV4AndForceR5SelectLegacyRevisions` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--force-R5` | `forceR5()` | — | `forceV4AndForceR5SelectLegacyRevisions` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |

## §5.11 Attachments (11)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--add-attachment` | `addAttachment(Consumer)` | ✓ | `addListShowRemoveAttachmentRoundTrip` | |
| `--key` | `AttachmentConfig.key` | ✓ | same | |
| `--filename` | `AttachmentConfig.filename` | — | `attachmentMetadataIsRecorded`; argv: `attachmentGroupsMapToTheirCliTokens` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--creationdate` | `AttachmentConfig.creationdate` | — | `attachmentMetadataIsRecorded`; argv: `attachmentGroupsMapToTheirCliTokens` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--moddate` | `AttachmentConfig.moddate` | — | `attachmentMetadataIsRecorded`; argv: `attachmentGroupsMapToTheirCliTokens` | argv: `everyEncryptionSubOptionMapsToItsCliToken` |
| `--mimetype` | `AttachmentConfig.mimetype` | ✓ | `attachmentMetadataIsRecorded` | |
| `--description` | `AttachmentConfig.description` | ✓ | `attachmentMetadataIsRecorded` | |
| `--replace` | `AttachmentConfig.replace()` | ✓ | `replaceAttachmentResolvesDuplicateKeys` | |
| `--copy-attachments-from` | `copyAttachmentsFrom(Consumer)` | ✓ | `copyAttachmentsFromWithPrefixResolvesConflicts` (self-made source; bytes verified) | |
| `--prefix` | `CopyAttachmentsConfig.prefix` | ✓ | `copyAttachmentsFromWithPrefixResolvesConflicts` | |
| `--remove-attachment` | `removeAttachment(String)` | ✓ | `addListShowRemoveAttachmentRoundTrip` | |

## §5.12 Inspection (16)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--is-encrypted` | `isEncrypted()` | ✓ | `isEncryptedDistinguishesEncryptedFiles` | |
| `--requires-password` | `requiresPassword()` | ✓ | `requiresPasswordRecognizesTheCorrectPassword` | |
| `--check` | `check()` | ✓ | `checkReportsOnValidFileWithoutErrors` | |
| `--show-encryption` | `showEncryption()` | ✓ | `hexEncryptionKeyOpensTheFileWithoutAPassword` | |
| `--show-encryption-key` | `showEncryptionKey()` | ✓ | same | |
| `--check-linearization` | `checkLinearization()` | ✓ | `checkLinearizationDistinguishesLinearizedFiles` (self-linearized output vs plain file) | |
| `--show-linearization` | `showLinearization()` | ✓ | `showLinearizationDumpsTheHintTables` | |
| `--show-xref` | `showXref()` | ✓ | `showXrefDumpsTheCrossReferenceTable` (per-object offsets and storage) | |
| `--show-object` | `showObject(String)` | ✓ | `showObjectPrintsTheTrailerDictionary` | |
| `--raw-stream-data` | `rawStreamData()` | ✓ | `rawStreamDataDeliversStreamBytesThroughTheSavePipeline` | |
| `--filtered-stream-data` | `filteredStreamData()` | ✓ | `filteredStreamDataDecodesTheContentStream` | |
| `--show-npages` | `showNpages()` | ✓ | `showNpagesReportsPageCount` | |
| `--show-pages` | `showPages()` | ✓ | `showPagesListsEveryPagesContent` | |
| `--with-images` | `withImages()` | ✓ | `withImagesAddsImageEntriesToThePageBlocks` (fixture `image-streams.pdf`) | |
| `--list-attachments` | `listAttachments()` | ✓ | `addListShowRemoveAttachmentRoundTrip` | |
| `--show-attachment` | `showAttachment(String)` | ✓ | same | |

## §5.13 JSON (9)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--json[=version]` | `json()` / `json(int)` | ✓ + version validation | `jsonRoundTripThroughOutputAndInput`; `jsonVersion1EmitsTheLegacyKeys` | |
| `--json-help` | — | — | N/A | CLI-only (§5.4) |
| `--json-key` | `jsonKey(String)` | ✓ | `jsonKeySelectsTheTopLevelKeys` | |
| `--json-object` | `jsonObject(String)` | ✓ | `updateFromJsonAppliesObjectChanges`, `jsonObjectRestrictsTheObjectsDictionary` | |
| `--json-stream-data` | `jsonStreamData(mode)` | ✓ | `jsonStreamDataInlineEmbedsBase64Data`, `jsonStreamDataFileWritesSideFilesWithThePrefix` | |
| `--json-stream-prefix` | `jsonStreamPrefix(String)` | ✓ | `jsonStreamDataFileWritesSideFilesWithThePrefix` | |
| `--json-output[=version]` | `jsonOutput()` / `jsonOutput(int)` | — | `jsonRoundTripThroughOutputAndInput`, `testJsonSchemaValidatesJsonOutput`; qpdf 12 accepts only `2`/`latest` here — the builder rejects other versions pre-native (`invalidJsonVersionIsRejectedBeforeTheNativeLayer`) | |
| `--json-input` | `jsonInput()` | — | `jsonRoundTripThroughOutputAndInput` | |
| `--update-from-json` | `updateFromJson(String)` | — | `updateFromJsonAppliesObjectChanges` | |

## §5.14 Global limits (7)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--global … --` | `global(Consumer)` | `globalGroupMapsToTheirCliTokens` | (entry point for the limits below) | per-job pinning documented in `QpdfJobImpl` |
| `--no-default-limits` | `noDefaultLimits()` | ✓ | `noDefaultLimitsLiftOptionalLimitsButKeepExplicitOnes` | |
| `--parser-max-nesting` | `parserMaxNesting(int)` | ✓ | `parserMaxNestingLimitFlagsDeeplyNestedData` (fixture `global.pdf`) | |
| `--parser-max-errors` | `parserMaxErrors(int)` | ✓ | `parserMaxErrorsLimitFlagsTheDamagedObject` (fixture `global_damaged.pdf`) | |
| `--parser-max-container-size` | `parserMaxContainerSize(long)` | ✓ | `parserMaxContainerSizeLimitFlagsLargeContainers` (fixture `global.pdf`) | |
| `--parser-max-container-size-damaged` | `parserMaxContainerSizeDamaged(long)` | ✓ | `tightDamagedContainerLimitTurnsRecoveryIntoAnError` | |
| `--max-stream-filters` | `maxStreamFilters(int)` | ✓ | justified N/A unless a multi-filter fixture is found | |

## §5.15 Testing/debugging (5)

| Feature | Java API | Argv test | Real-PDF test | Notes |
|---|---|---|---|---|
| `--static-id` | `staticId()` | ✓ | `staticIdRepeatsTheDocumentId` (forces ID generation via a same-job update-from-json; asserts qpdf's fixed test constant) | |
| `--static-aes-iv` | `staticAesIv()` | ✓ | `staticAesIvYieldsByteIdenticalEncryptedOutput` (128-bit AES per qpdf's recipe; the default IV is random) | |
| `--linearize-pass1` | `linearizePass1(String)` | ✓ | `linearizePass1WritesTheIntermediateFile` | |
| `--test-json-schema` | `testJsonSchema()` | ✓ | `testJsonSchemaValidatesJsonOutput` | |
| `--report-memory-usage` | `reportMemoryUsage()` | ✓ | `reportMemoryUsageAppearsInTheWarningStream` | |

## Cross-cutting grammars (spec §10)

Page ranges (§10.1) are exercised through the options that consume them
(`pages` ranges, `rotate` spec, overlay `to`/`from`/`repeat`, label specs);
the grammars themselves belong to qpdf, not jqpdf. PDF dates (§10.8) are
exercised by the attachment date tests (task 8).
