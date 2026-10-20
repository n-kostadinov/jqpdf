# Overlay and underlay

Covers the qpdf CLI's *Overlay and Underlay* area (§5.9): drawing another
file's pages on top of (overlay) or beneath (underlay) the output.

```java
// stamp.pdf page 1 onto output pages 1-5 (cycling its page 4 after that)
Jqpdf.createJob()
        .input("in.pdf").output("out.pdf")
        .overlay(o -> o
                .file("stamp.pdf")
                .to("1-5")
                .from("1-3")
                .repeat("4"))
        .run().orThrow();
```

Semantics:

- Source pages are consumed in order onto corresponding output pages until
  either side runs out; extra output pages are left alone.
- `to(range)` — destination pages (default: all).
- `from(range)` — source pages in order (default: all); may be empty
  (`from("")`) when only `repeat(...)` is used.
- `repeat(range)` — pages cycled after the `from` pages are exhausted; the
  classic odd/even footer is `from("").repeat("1,2")`.
- `password(...)` opens an encrypted stamp file.

Overlays and underlays are processed after merges and page selection, so
they always apply to the final page layout. Combine freely with
`underlay(...)`; stacking order is: underlays in call order, the original
page, then overlays in call order.
