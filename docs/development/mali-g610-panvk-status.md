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


## Update from the 2026-10-07 v10 worklog and later driver notes

The upstream Firebase v10 worklog records a newer G610 MC3 run:
- beta.18-derived build: PanProbe 36/36 on Mali-G610 MC3 (MT6879, Android 14,
  Linux 5.10.218, CSF 1.18, 4 KiB pages);
- the same run passed DXVK, Bachata S4 and vkd3d compliance;
- BC emulation was enabled with native compressed-format mask
  `0xc1fe039e`; `KBASE_IOCTL_MEM_ALLOC_EX` returned ENOTTY and fell back
  to `MEM_ALLOC`; HIDL mapper4 was used and swapchain lifecycle passed.
This is a concrete G610 validation result, but it is still one kernel/ROM
combination. The MC4 and MC6 results above remain earlier-build results and
must not be silently upgraded to beta.18 results.

A later DroidDeck non-Adreno worklog marks the CSF 32-bit address truncation
bug as fixed across the kbase, G615, G710 and G720 CSF PanVK builds. It was
found on G720 and ported across the CSF variants. Do not keep this as an
unfixed G610 issue; still retain a regression test when a device reproducer
is available.

The Redmi Note 13 Pro+ G610 report remains open: GPU shareability memory
fault, then queue timeouts; a separate PanVK report recorded CSF fatal error
`0x88` followed by `VK_ERROR_DEVICE_LOST` after about two minutes. No
matching upstream fix is recorded. Patch 111 only changes Vulkan SetEvent /
ResetEvent error reporting after a failed event notification; it does not
repair an underlying CSF fatal fault or memory shareability violation.

## Patch applicability guardrails

- `csf-v11/108` is the candidate partial-native-BC fix. Its recorded hardware
  validation is Mali-G57 v9 JM, not G610. The G610 MC3 beta.18 worklog shows
  BC emulation active and all relevant game checks passing, but the exact
  source/patch hash must be pinned before deciding whether 108 is already
  included in a build. Do not reapply it blindly.
- `csf-v11/110` changes CSF ioctl layouts, page-size handling and
  MEM_ALLOC_EX fallback. Its hardware validation is JM v9 for shared kmod
  paths; its CSF paths were not run on hardware in the patch notes. G610 MC3
  beta.18 confirms MEM_ALLOC_EX ENOTTY fallback to MEM_ALLOC on that device,
  but does not independently validate every ioctl layout.
- `csf-v11/111` is Vulkan API error-code hygiene, not a CSF fault fix.
- `csf-v11/118` adds device, gpu_id, texture-feature and BC-emulation logs.
  This is useful diagnostic instrumentation and does not change behavior.
- `csf-v11/186` handles a 40-byte MediaTek SLC PBHA variant of
  MEM_IMPORT observed on MT6993 / Mali-G1-Ultra v14. It is not a G610 fix.
  Only consider it for a G610 kernel if logs or headers prove the same
  extended ioctl layout is present.
- `profiles/g610-v10-csf.json` still says `planned P24 (not yet validated)`
  while the v10 worklog contains G610 MC3 validation. Treat the profile
  status as stale/inconsistent metadata, not as evidence that the MC3 test
  did not happen.

## Current diagnostic priority for the unresolved Redmi G610 fault

Capture the first fault, not just the eventual queue timeout:
1. kbase uAPI version, page size, gpu_id/variant and CSF queue-group layout;
2. first CSF fault status/address and whether the address is truncated;
3. imported BO attributes and shareability/cacheability flags, including
   dma-buf exporter/importer memory type and heap;
4. GPU VA range and page-table mapping around the fault;
5. queue/group state immediately before and after fatal error 0x88;
6. whether the failure occurs in the Android WSI/Gralloc path or in a
   headless/offscreen Vulkan workload.

The previously reported 32-bit VA issue is marked fixed, so the next
reproducer should explicitly verify the full-width address and then focus on
shareability and imported-memory attributes. Do not treat a later timeout as
the root cause if an earlier fault is available.


## Upstream snapshot checked 2026-11-06

The current `zenithblue-oss/panvk-kbase-android` main README advertises
Mesa PanVK v26.3.1 / build 122i as a universal v6-v14 driver, but its current
tested-device table does **not** list G610; it explicitly says G610, G710,
G715 and several other models are untested in that release matrix. This is a
statement about the current universal build, not proof that no G610 testing
has ever occurred.

The same repository's changelog records earlier G610 evidence on the pinned
Mesa 26.3.0-devel tree (`5a07217f034b`):
- beta.18 combined build: G610 MC3 PanProbe 37/37 and PanPlay D3D11 cube;
- RC3: G610 MC3 PanProbe 38/38, alongside G710/G715 and newer CSF devices;
- RC3 changelog also records v10 `robustImageAccess2` validation at 38/38.

Therefore the correct status is: **G610 MC3 has prior device validation on
specific earlier builds, but the current v26.3.1 universal release is not
listed as G610-tested; G610 MC4/MC6 remain separately unverified on that
current build.** The standalone `profiles/g610-v10-csf.json` still says
`planned P24 (not yet validated)`, which conflicts with the historical
worklog/changelog. Keep both facts visible until upstream reconciles the
profile metadata. Do not infer MC4/MC6 validation from MC3.

The current README describes BCn emulation and Gralloc support as implemented
in the universal driver. That does not establish that the old MC4 BC/mapper
failures reproduce on v26.3.1; they need retesting against the exact binary
and matching Android/kernel combination. The unresolved DroidDeck G610
shareability/queue-timeout report remains a separate issue.

Current source identity reported by the release README: Mesa
26.3.0-devel, git `795ca7ff3e`, patch marker `p0175`, dated 2026-10-31.
The repository's `sources.lock` still pins Mesa commit
`5a07217f034b3e50d8c7c7794f97a2df1742613b` with patch-series ID
`sha256:374b7b830111a848515d3e0ec41a60b902bc57938deb420ecea7de8a03f58ecd`.
These are different source identities and must not be treated as interchangeable.
