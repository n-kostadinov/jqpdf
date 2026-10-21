# Page selection and modification

Covers the qpdf CLI's *PDF Modification* area (§5.8): selecting, merging,
collating, splitting, rotating, and labeling pages, plus appearance handling,
image optimization, and the remove-* family. Page-range grammar: spec §10.1.

## Selecting and merging pages

`pages(...)` configures page groups; each `file(...)` starts a group and the
optional `range(...)`/`password(...)` apply to it. `"."` refers to the
primary input file.

```java
// take pages 1-3 of a.pdf, then the last two of b.pdf
Jqpdf.createJob()
        .input("a.pdf").output("merged.pdf")
        .pages(p -> p
                .file(".").range("1-3")
                .file("b.pdf").range("z-1"))
        .run().orThrow();
```

Notes (spec §10.6):

- Document-level data (outlines, threads) comes from the primary input only;
  combine with `emptyInput()` to drop it entirely.
- Selecting the same page twice shares its objects. Give the file two
  different paths (e.g. `./in.pdf` vs `in.pdf`) to get independent copies.
- Page ranges accept `n`, `r<n>` (n-th from the end), `z` (last),
  `a-b` (inclusive, reversed when a > b), comma lists, `x` exclusions, and
  `:odd`/`:even` suffixes, e.g. `"1,6,4"`, `"r3-r1"`, `"1-20:even"`.

## Collating

`collate()` interleaves pages round-robin across the groups instead of
concatenating; `collate("1,2")` takes 1 page from the first group, 2 from
the second, cycling.

## Splitting

`splitPages()` (or `splitPages(n)`) writes each page group to its own file.
Use `%d` in the output name; it is replaced with a zero-padded page range
(`out-%d.pdf` → `out-01.pdf`, `out-02.pdf`, …).

```java
Jqpdf.createJob()
        .input("in.pdf").output("page-%d.pdf")
        .splitPages()
        .run().orThrow();
```

Split outputs are fresh single-page documents — no document-level data.

## Rotation and labels

- `rotate("+90:2,4-6")` — repeatable; `+`/`-` relative, bare angles absolute
  (0/90/180/270); omitted range means all pages.
- `setPageLabels("1:r", "5:D/10", "12:")` — one label spec per section, first
  at page 1, increasing; grammar `first-page:type[/start[/prefix]]` with
  types `D` `A` `a` `R` `r` (empty type = prefix only).

## Annotations, forms, and images

- `flattenAnnotations(FlattenAnnotations.ALL|PRINT|SCREEN)` — push
  annotations into page content (freezes placement); run
  `generateAppearances()` first if form appearances are stale.
- `generateAppearances()` — regenerate stale form-field appearances.
- `removeAcroform()` — drop the interactive form dictionary.
- `optimizeImages()` — recompress non-JPEG images as JPEG when smaller and
  within the thresholds (`oiMinWidth`, `oiMinHeight`, `oiMinArea`); inline
  images are externalized unless `keepInlineImages()`.
- `flattenRotation()` — bake `/Rotate` into page contents.

## The remove-* family

`removeInfo()`, `removeMetadata()`, `removePageLabels()`, `removeStructure()`
drop the corresponding document metadata.
