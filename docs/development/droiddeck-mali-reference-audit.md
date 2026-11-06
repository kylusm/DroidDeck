# Reference audit: jdmg28/DroidDeck-Mali

Checked 2026-11-06 against `jdmg28/DroidDeck-Mali` branch
`DroidDeck-mali-experimental`. This is an engineering reference, not a code
port. Its tested target is Mali-G615 MC6 on a Dimensity 8300-Ultra (POCO X6
Pro); our target remains the broader Mali-G610 family on several different
MediaTek SoCs.

Repository: https://github.com/jdmg28/DroidDeck-Mali
Branch: https://github.com/jdmg28/DroidDeck-Mali/tree/DroidDeck-mali-experimental
Audit: https://github.com/jdmg28/DroidDeck-Mali/blob/DroidDeck-mali-experimental/docs/mali-experimental-audit.md

## What is reusable

1. **A separate Mali driver path in DroidDeck.** The fork has explicit GPU
   detection, Mali driver profiles, separate driver import/selection, and
   experimental gating. This is useful architectural precedent for keeping
   PanVK-kbase separate from Adreno/Turnip.
2. **A paired runtime/display driver model.** It distinguishes the Android
   display-side library from the glibc Linux runtime library. We should keep
   the exact binary pair, Mesa revision, patch set, ABI and checksums together
   rather than mixing independently sourced halves.
3. **A reproducible package workflow.** Its
   `.github/workflows/mali-panvk-driver.yml` pins an upstream release tag and
   asset SHA-256, checks AArch64 and glibc linkage, builds a package manifest,
   calculates a package checksum and verifies publication. This is useful
   packaging/CI practice, although it downloads a prebuilt backend rather than
   building Mesa from source.
4. **Evidence must be tied to the exact binary.** The fork's own
   `docs/mali-experimental-audit.md` explicitly notes that several WSI,
   swapchain and AHB validation records refer to a different backend SHA than
   the one in the shipped kit. This is an important lesson: enumerate,
   device-create, compute, offscreen, AHB, surface and swapchain results must
   each identify the exact binary SHA and device/software environment.
5. **Keep a safe default.** The fork defaults to software mode and requires an
   explicit experimental selection before exposing the Mali GPU to the guest.
   That is a sensible safety boundary while kernel/firmware hangs remain
   possible.

## What must not be copied as-is

- **Do not copy its fake DRM-node mapping.** The fork's audit describes
  mapping `/dev/mali0` to a guest `/dev/dri/renderD*` path and synthesizing
  DRM sysfs metadata so gamescope sees a DRM primary/render node. A kbase
  device is not a DRM render node. Re-labeling it does not make DRM ioctls,
  dma-buf synchronization, DRM modifiers or WSI semantics valid. The audit
  itself flags the ioctl-collision question as unverified. We should solve
  the compositor/WSI integration through a real supported interface or a
  narrowly scoped, validated compatibility layer, never by assuming kbase
  and DRM are interchangeable.
- **Do not treat its ICD shim as a GPU driver fix.** The shim forwards to
  PanVK and synthesizes `VK_EXT_physical_device_drm` metadata for gamescope;
  it does not implement kbase, memory management, synchronization, submission,
  or GPU features. Any such metadata must not claim unsupported capabilities.
- **Do not take the opaque backend binary as our base.** The audit identifies
  a backend without corresponding source/reproducible build provenance and
  mismatched validation hashes. Use source-pinned Mesa/PanVK-kbase builds
  with recorded patches and hashes.
- **Do not transplant G615-specific assumptions.** Its G615 MC6 / Dimensity
  8300-Ultra kernel, firmware, memory topology, Android release, and kbase
  ABI behavior are not proof for G610 MC3/MC4/MC6. The CPU difference itself
  is not the central issue for a GPU userspace driver; the GPU revision,
  kernel kbase UAPI, firmware, page size, DMA-BUF/Gralloc and memory
  attributes are.

## Implications for our G610 work

- Keep our existing G610 family and MC-variant tests; do not rename the
  target to G615 or add POCO-specific detection.
- Add a provenance manifest to every future G610 driver artifact:
  Mesa commit, patch-series digest, build configuration, Android/Linux ABI,
  kernel interface (CSF), GPU model/revision, device/ROM/kernel tuple,
  artifact SHA-256 and test-result references.
- Split runtime validation into gates: ICD enumeration; physical-device
  properties; device creation; compute; offscreen rendering; DMA-BUF/AHB
  import/export; Vulkan surface; swapchain lifecycle; sustained workload;
  cold relaunch. Each gate records the exact artifact SHA.
- Keep direct kbase access and any compositor-facing compatibility layer
  separate. A gamescope workaround must not silently alter the GPU driver's
  memory or submission behavior.
- Treat any device freeze/reboot as a stop condition. Do not enable automatic
  GPU probing or expose `/dev/mali0` to every session while the driver is
  experimental.

## Current integration CI checkpoint

Our `feat/panvk-mali-g610-universal` workflow run
https://github.com/kylusm/DroidDeck/actions/runs/42208571251 completed
successfully on commit `b2046082b5a8b5237c0b314489496c8015a7cf48`.
The workflow ran the two G610 JVM test classes with Gradle's
`testReleaseUnitTest` task. This validates app-side detection and package
selection only; it does not build Mesa, load PanVK, or validate real GPU
execution on a handset.
