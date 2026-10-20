# Encryption options

Covers the qpdf CLI's *Encryption* area (§5.10): creating encrypted output,
removing or copying encryption, and the permission restrictions an encrypted
file can carry. Full semantics live in the qpdf manual's encryption notes
(§10.2 of the spec).

## Encrypting

The `encrypt(...)` group takes a lambda; `bits40()/bits128()/bits256()` is
mandatory and must come before the restriction options. Passwords may be
empty strings (`""`).

```java
Jqpdf.createJob()
        .input("in.pdf").output("out.pdf")
        .encrypt(enc -> enc
                .bits256()
                .userPassword("user")
                .ownerPassword("owner")
                .print(EncryptionConfig.Print.LOW)      // low-quality printing only
                .modify(EncryptionConfig.Modify.NONE))  // no modifications
        .run().orThrow();
```

Key lengths:

- `bits256()` — AES-256, the default choice for new files (needs PDF ≥ 1.7
  ext 8). Extra options: `allowInsecure()` (empty owner password — testing
  only), `forceR5()` (deprecated algorithm, testing only).
- `bits128()` — RC4 or AES (`useAes(true)`); extra option `forceV4()`.
- `bits40()` — RC4, 40-bit; writing it requires `allowWeakCrypto()` on the
  job, otherwise the run fails with exit code 2.

Restriction options (`accessibility`, `annotate`, `assemble`, `extract`,
`form`, `modifyOther`, `modify(level)`, `print(level)`) default to fully
permissive; opt into restrictions. Enforcement is up to viewers — qpdf
itself always processes the file.

## Removing and copying encryption

- `decrypt()` — write unencrypted output even when the input is encrypted.
- `removeRestrictions()` — drop restrictions associated with signed files;
  invalidates digital signatures.
- `copyEncryption(file)` — reuse user password, owner password,
  restrictions, and half of `/ID` from another file; the password for that
  file goes in `encryptionFilePassword(...)`, not `password(...)`.

To *test* encryption state rather than change it, see `isEncrypted()` /
`requiresPassword()` in [inspection](inspection.md).
