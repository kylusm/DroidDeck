# PanVK Mali artifact provenance audit

Date: 2026-11-06

Scope: determine what the currently published DroidDeck PanVK Mali pair proves, what its build actually contains, and what must be pinned before reproducing it in this repository. This is an artifact/source audit, not a claim of G610 runtime validation.

## Executive result

The existing `PanVK-Mali-G710-Pair.zip` is a useful experimental G610-class package reference, but it is **not evidence that the current universal PanVK release has been validated on Mali-G610**.

The release metadata says:

- Release repository: `The412Banner/droiddeck-nonadreno-drivers`
- Release tag: `panvk-mali-g710`
- Linux source repository: `GunaCharanTeja/panvk-kbase-android`
- Source ref: `aa01eb0cd2d5e622ffbaa3a1e097857fe8ed8719`
- Profile: `g610-v10-csf`
- Mesa base: `5a07217f034b3e50d8c7c7794f97a2df1742613b` (Mesa 26.3.0-devel)
- Minimum Linux glibc reported by the release: 2.38
- Pair asset SHA-256 (GitHub release digest): `2ec4c64801c03c7686a1b95778c114cbdd0fa38da28cc7ac4d95180322a81be0`

The matching upstream profile currently describes Mali-G610 / arch v10 / CSF / mali_kbase, but its status is still `planned P24 (not yet validated)`. The upstream README also lists G610 among untested GPU models. Therefore this package is a reproducible *candidate build identity*, not a G610 qualification result.

## Important source distinctions

Do not conflate these three identities:

1. The upstream `sources.lock` Mesa commit: `5a07217f034b3e50d8c7c7794f97a2df1742613b`.
2. The **G710 pair's pinned source ref** (`GunaCharanTeja/panvk-kbase-android` at `aa01eb0cd2d5e622ffbaa3a1e097857fe8ed8719`) has patch-series ID `sha256:2fead4a864a0ed32f27993c4956736f0ef5ba17d6689429ad9ff232832b2a015`.
3. The separate current `zenithblue-oss/panvk-kbase-android` main tree has patch-series ID `sha256:374b7b830111a848515d3e0ec41a60b902bc57938deb420ecea7de8a03f58ecd` in its current `sources.lock`.
4. The newer upstream README's runtime `driverInfo` example: Mesa 26.3.0-devel, git `795ca7ff3e`, patch marker `p0175`, dated 2026-10-31.

These are not interchangeable. The G710 pair's release corresponds to the older GunaCharanTeja source pin, whose lock file declares `2fea...`; the current zenithblue main tree declares `374b...`. The newer README identity also does not establish that the older DroidDeck G710 pair was built from that newer tree.

**Integrity issue found by our CI:** running the pinned GunaCharanTeja commit's own `scripts/compute-patch-series-id.py --profile g610-v10-csf` produced `sha256:bad3cffc8e1229ac8d903c90fc9451694ce04ccb760de393378b5ed02c602c51`, which does not match the `2fea...` value declared in that same commit's `sources.lock`. The immutable source ref, Mesa commit, and G610 profile assertions passed. The source-lock history shows its last update was on 2026-10-14, while the selected source ref is later; subsequent patch-series changes are a likely reason for the stale lock value, but this still needs confirmation from upstream history. Until the upstream lock/script discrepancy is explained and reconciled, the declared patch-series ID must be treated as unverified and this source pin must not be used for a reproducible release.

## Build pipeline findings

### Upstream PanVK repository

At the time of this audit, the upstream `zenithblue-oss/panvk-kbase-android` main tree is `cf70e02b918e1864cbcd746a7fefa57b96c673ce`.

Its `.github/workflows/build.yml` does **not** build Android or Linux driver binaries. It checks that `sources.lock` contains a 40-character Mesa commit, fetches the pinned Mesa source, applies the `g615-v11-csf` patch profile, captures an upstream matrix, and uploads that matrix as an artifact.

The actual build recipes are in `scripts/build-android.sh` and `scripts/build-glibc.sh` (plus dependency/bootstrap and packaging scripts). The Android build uses an NDK cross toolchain, host Mesa code-generation tools, Android dependency libraries, Meson, and Ninja. A full build should not be inferred from the source-validation workflow passing.

### Existing DroidDeck non-Adreno release pipeline

The G710-specific workflow calls the shared `.github/workflows/_driver.yml` reusable workflow with:

- `kind: patchset`
- `profile: g610-v10-csf`
- `kmds: kbase`
- the pinned source ref above.

The reusable workflow builds a glibc/Linux half, then independently checks out the same source ref and builds an Android half. Both apply the selected profile's patch series. However, the Android step additionally runs `patches/panvk_csf_provoking_vertex_u64.py` against its Mesa tree. That extra Android-only patch means the two halves should not be described as byte-for-byte identical source trees unless the build is changed to apply and record the same overlay on both sides (or the difference is proven irrelevant and explicitly recorded).

The release pair therefore provides useful package-layout and source-pin precedent, but its notes alone are not a complete per-half source manifest.

## What DroidDeck already verifies

The app's non-Adreno release parser reads GitHub's asset `digest` field through `Hashes.githubSha256`, retains the SHA-256 with the asset, and uses it for non-Adreno installed-build/update checks. This is a useful integrity check for the downloaded archive.

An archive digest proves the bytes match the GitHub release asset digest. It does **not** prove which Mesa commit, patch set, build flags, compiler, Android NDK, or Linux sysroot produced those bytes. Those require an embedded provenance manifest and a clean build record.

## Required manifest before we publish our own PanVK package

Each Android and Linux half must carry a machine-readable manifest (or a pair manifest with explicit per-half records) containing at least:

- schema version and package/pair identifier;
- source repository URL and immutable source commit;
- Mesa repository URL and immutable Mesa commit;
- profile name and exact profile JSON digest;
- patch-series ID plus any extra overlay patch paths and SHA-256 values;
- build script revision and complete Meson configuration;
- compiler, linker, NDK (Android), target API, host image, and Linux sysroot identity;
- Android ABI / Linux ABI and minimum Android API / glibc;
- GPU family/profile intent (not a claim of device qualification);
- output library names and SHA-256 for each library;
- package SHA-256 for each archive;
- test stage results, each bound to the exact library SHA-256 and device/kernel tuple;
- build workflow URL and run ID.

A release should fail closed if any source ref is moving, any required digest is absent, or Android/Linux halves disagree with the declared pair manifest.

## G610 qualification gates

Keep the GPU validation stages separate and attach each result to the exact driver library SHA-256:

1. ICD discovery and Vulkan instance creation;
2. physical-device enumeration and properties;
3. logical-device creation;
4. bounded compute smoke test;
5. offscreen rendering and readback;
6. DMA-BUF / AHardwareBuffer import-export;
7. Android surface and swapchain creation;
8. sustained, bounded workload and cold relaunch.

A pass at one stage does not imply later stages pass. Stop on a kernel fault, device freeze, or repeated CSF submission failure; preserve logs and reboot rather than retrying GPU submissions indefinitely.

## Build-resource decision

Do not equate a developer's available local disk space with GitHub-hosted runner capacity. Before enabling a full Mesa build in Actions, add a diagnostic-only job that records runner disk capacity/free space, source checkout size, dependency size, and peak build-directory size. Then choose among a hosted runner, a documented self-hosted runner, or split build jobs based on measured peak usage. The current G610 JVM test workflow should remain lightweight and independent of this build path.

## References

- PanVK source lock: https://github.com/zenithblue-oss/panvk-kbase-android/blob/main/sources.lock
- G610 profile: https://github.com/zenithblue-oss/panvk-kbase-android/blob/main/profiles/g610-v10-csf.json
- Upstream build workflow: https://github.com/zenithblue-oss/panvk-kbase-android/blob/main/.github/workflows/build.yml
- Upstream Android build recipe: https://github.com/zenithblue-oss/panvk-kbase-android/blob/main/scripts/build-android.sh
- Upstream release process: https://github.com/zenithblue-oss/panvk-kbase-android/blob/main/docs/RELEASE.md
- DroidDeck G710 build workflow: https://github.com/The412Banner/droiddeck-nonadreno-drivers/blob/main/.github/workflows/panvk-mali-g710.yml
- DroidDeck shared build workflow: https://github.com/The412Banner/droiddeck-nonadreno-drivers/blob/main/.github/workflows/_driver.yml
- G710 pair release: https://github.com/The412Banner/droiddeck-nonadreno-drivers/releases/tag/panvk-mali-g710


## First audit CI run

- Run: https://github.com/kylusm/DroidDeck/actions/runs/42210223461
- Source checkout: passed at `aa01eb0cd2d5e622ffbaa3a1e097857fe8ed8719`.
- G610 profile / Mesa commit assertions: passed.
- Patch-series identity: failed (`2fea...` locked vs `bad3...` recomputed).
- Mesa fetch and patch-application stages: skipped in this first run because the initial workflow version stopped immediately on the mismatch.
- No driver build was attempted.

Second run: https://github.com/kylusm/DroidDeck/actions/runs/42210344262
- Exact Mesa commit fetch: passed; shallow checkout occupied about 547 MB.
- PanVK source checkout: about 96 MB.
- G610 profile patch application: passed, all 20 selected patches applied cleanly to the pinned Mesa commit.
- Patch-series identity gate: failed, as intended, because the lock value and recomputed digest differ.
- Runner filesystem after source fetch and patch application: 145 GB total, about 85 GB available. This is a measured snapshot before any full driver build, not a peak-build estimate.
- No driver binary was compiled.

The workflow now continues through Mesa fetch and patch applicability while preserving a failing result for the unresolved digest mismatch.
