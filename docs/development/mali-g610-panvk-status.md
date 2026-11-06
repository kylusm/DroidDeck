# Mali-G610 / PanVK-kbase engineering status

Status: investigation and integration tests; no G610 binary is certified by this repository.

## Scope

Target the Mali-G610 GPU family (Mesa PAN_ARCH v10, CSF frontend) through the
generic Android `mali_kbase` interface. Do not key the implementation to a
phone brand or retail model. A device model is useful only as a validation
record because the vendor kernel, kbase UAPI, gralloc mapper, page size and
memory policy can differ between devices.

DroidDeck already:
- identifies several MediaTek SoCs carrying Mali-G610;
- maps G610 to `Family.MALI_G610_G710`;
- offers the G610/G710 PanVK pair and the generic v9-v13 kbase pair;
- prefers the generic kbase pair at Android API 35+, subject to an available
  release asset.

The generic kbase binary and Android display/WSI path are separate concerns.
A Linux PanVK half finding the GPU does not prove that Android's Vulkan loader,
Gralloc, DMA-BUF import/export, or swapchain path works.

## Evidence and limitations

The upstream `zenithblue-oss/panvk-kbase-android` v10 status notes record:
- Mali-G610 MC3 (MT6879, Android 12, Linux 5.10, CSF 1.18): 36/36 PanProbe
  tests on a beta.17-derived build with follow-up patches.
- Mali-G610 MC4 (MT6886, Android 13, Linux 5.15): 15/17 on beta.15. The two
  failures were BC decode/feature exposure and Android swapchain lifecycle
  (Gralloc mapper metadata loading).
- Mali-G610 MC6 on Android 12 / Linux 5.10: earlier PanPlay runs were rejected
  by DXVK because `textureCompressionBC` was not exposed.

These results are not interchangeable: they use different kbase kernels,
Android releases and driver patch revisions. The 36/36 result is not a blanket
G610 certification. The upstream profile `profiles/g610-v10-csf.json` still
labels its profile planned, so the profile metadata and the later v10 worklog
need reconciliation upstream.

The DroidDeck non-Adreno driver worklog also records later G610 failures:
- Redmi Note 13 Pro+: CSF fatal error `0x88` followed by
  `VK_ERROR_DEVICE_LOST` after roughly two minutes.
- A G610 shareability-memory fault followed by queue timeouts.
- A G610 bridge/display path that could not share a compatible memory type
  with gamescope on an r32p1 driver.

These are separate failure classes. Do not assume the BC workaround fixes
GPU-VA truncation, shareability, or Gralloc/WSI.

## Investigation order

1. **Reproduce the exact build:** pin Mesa commit, patch series, Android and
   Linux halves, and release asset hashes. Never compare only a marketing
   version string.
2. **Capture kbase identity before Vulkan:** GPU ID/revision, PAN_ARCH, JM/CSF,
   kbase UAPI/DDK version, page size, shader-core mask, coherency and
   `/dev/mali0` access as the actual app UID.
3. **Separate feature exposure from execution:** inspect BC format properties
   and `textureCompressionBC`; run the BC decode workload independently of
   DXVK adapter selection.
4. **Separate WSI from core Vulkan:** test loader/instance/device/queues first,
   then mapper metadata, AHB/Gralloc, DMA-BUF modifiers and swapchain lifecycle.
5. **Trace device loss:** capture the first GPU fault, VA width, memory
   shareability attributes, queue/group state and kbase ioctl return values
   before the eventual timeout. The first fault is more useful than the final
   `DEVICE_LOST`.
6. **Only then change Auto selection:** retain conservative API-level and
   asset-availability checks. Never label an unvalidated device as tested.

## DroidDeck regression coverage

`app/src/test/java/com/droiddeck/launcher/gpu/MaliG610PanvkTest.kt` covers:
- known MediaTek SoC-to-G610 family mappings;
- kbase coverage of G610;
- API 34 generation-pair vs API 35 generic-kbase selection;
- pair completeness and rejection of an unrelated G52.

The focused GitHub Actions workflow is
`.github/workflows/mali-g610-tests.yml`. Its result must be checked before
calling this test change validated.

## Out of scope

- No phone-brand-specific driver or GPU identity mapping.
- No claiming all G610 MC3/MC4/MC6 devices work from one successful test.
- No changing upstream Mesa feature reporting solely to make DXVK accept the
  device; Vulkan capabilities must match actual implementation and tests.
- No copying an old patch into the current tree without checking whether its
  fix is already present in the pinned source.
