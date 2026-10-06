# GhostSam

**English** | [中文](README.zh.md)

Kernel privilege-escalation toolkit for Samsung Galaxy S22 / S26 / Z Fold8
devices (Android 12 and up, kernels 5.10-6.18), with an Android manager app
that drives the whole flow over ADB.

The tree ships three device-specific Ghostlock exploit chains (race-based),
a dirtyfrag install chain (matched by KMI), a shared userspace layer, and
the app that packages the prebuilt payloads.

## Overview

- **Target devices**: Galaxy S22 (`s22`), Galaxy S26 (`s26`) and Galaxy
  Z Fold8 (`zf8`), on Snapdragon and Exynos SoCs.
- **Install paths**:
  - **dirtyfrag (recommended, faster)**: a single run deploys the KernelSU
    kernel module (page-cache 4-byte store via xfrm-ESP, CVE-2026-43284)
    and late-loads it directly; there is no race to win.
  - **Ghostlock chains**: race-based exploits for the selected series; the
    device reboots during the run, after which the app completes the
    KernelSU late-load.
- **Memory-corruption primitive (CVE-2026-43499)**: a futex PI use-after-free
  race is turned into a single controlled 8-byte kernel write; a `misc` /
  `simple_attr` file descriptor serves as the write target, from which
  arbitrary kernel read/write is built. The Z Fold8 chain uses an `io_submit`
  based write path.
- **Root stage**: a forged `work_struct` is queued on `system_unbound_wq`
  with `call_usermodehelper_exec_work` as its function, so the kernel
  starts our helper with init credentials; no credential structure is
  modified, and KDP's EL2 guard sees nothing.
- **Root channel**: `su_daemon` keeps a root shell on
  `/data/local/tmp/temp_su.sock` for the session.
- **Persistence (temporary root)**: KernelSU or KernelSU-Next is
  late-loaded; root is kept until the next reboot (re-run the chain to
  re-apply). In the Ghostlock chains the `ksud` binary is bind-mounted over
  a dormant system binary, so path-based DEFEX rules only ever see a
  whitelisted target.

## Supported devices

### Ghostlock chains (matched by device)

Firmware is matched per kernel baseline, not per build number. Every series
ships a generated table: one entry per kernel baseline (17-18 symbol
offsets) and one row per known build; matching happens at runtime on
series / codename / model / build, and unknown combinations fail closed
(no kernel writes are attempted).

| Series  | Baselines | Known builds | Devices / notes                                               |
| ------- | --------- | ------------ | ------------------------------------------------------------- |
| s22     | 4         | 66           | SM-S9010 / S9060 / S9080 ... (Snapdragon, Exynos 2200)        |
| s26     | 4         | 153          | SM-S9420 / S9470 / S9480 ... (Snapdragon, Exynos), One UI 8.5 |
| s26-ui9 | 2         | 6            | One UI 9 geometry, executed by the io_submit chain            |
| zf8     | 6         | 73           | SM-F9710 / F9760 ... (Z Fold8)                                |

A custom baseline for unlisted firmware can be supplied at runtime through
`/data/local/tmp/ghostsam-lines.conf`; the format is validated by
`exploit/src/common/params_custom.h`.

### dirtyfrag chain (matched by KMI)

The dirtyfrag chain ships one kernel module per KMI (`dfr_lkm-<kmi>.ko` in
the app assets); the app selects the module matching the device's kernel.
Shipped KMIs:

| KMI            | Status   | Notes                                       |
| -------------- | -------- | ------------------------------------------- |
| android12-5.10 | Tested   |                                             |
| android13-5.10 | Untested |                                             |
| android13-5.15 | Tested   |                                             |
| android14-5.15 | Untested |                                             |
| android14-6.1  | Partial  | only kernels ≤6.1.71; 6.1.72+ not supported |
| android15-6.6  | Tested   |                                             |
| android16-6.12 | Tested   |                                             |
| android17-6.18 | Untested |                                             |

## Repository layout

```
app/                             Android manager app (Kotlin / Jetpack Compose)
  app/src/main/assets/payloads/  prebuilt payloads staged into the APK
exploit/
  src/common/                    shared layer: logging, options, parameter
                                 tables, KernelSnitch probe, KSU protocol,
                                 su_daemon, boot log, chain skeleton
  src/chains/pselect/            pselect write chain + attr carrier + UMH root
  src/chains/iosubmit/           io_submit write chain (zf8, s26 on One UI 9)
  src/chains/dirtyfrag/          xfrm-ESP page-cache store installer + LKM
                                 loader
  src/series/s22|s26|zf8/        per-series entry points, parameter tables,
                                 and assembly
```

## Building

### App

All payloads are prebuilt under `app/app/src/main/assets/payloads/`, so the
APK builds standalone:

```bash
cd app
./gradlew assembleRelease
```

The `:app:verifyPayloads` Gradle task validates the payload set during the
build: each series folder must provide `preload.so` and `su_daemon`,
`payloads/ksud/` must provide `ksud` and `ksud-next`, and
`payloads/dirtyfrag/` must provide `dfr_payload` plus one
`dfr_lkm-<kmi>.ko` for every supported KMI.

### Native payloads

Building the native side needs an Android NDK r30 (or a host clang with an
aarch64 sysroot). `exploit/Makefile` is the single build entry point;
artifacts land under `exploit/build/`:

```bash
cd exploit
make            # all series + dirtyfrag payloads
make s26        # or a single target: s26 / s22 / zf8 / dirtyfrag
```

### Dirtyfrag kernel module

The LKM is an out-of-tree kbuild target and needs a prepared kernel build
directory; Android DDK containers provide one per KMI:

```bash
make lkm KDIR=/path/to/kernel-build-dir
```

### ksud binaries

`payloads/ksud/ksud` and `payloads/ksud/ksud-next` are prebuilt release
builds (aarch64-linux-android) from our KernelSU forks,
[snothin/KernelSU](https://github.com/snothin/KernelSU) and
[snothin/KernelSU-Next](https://github.com/snothin/KernelSU-Next); rebuild
them from those repositories when bumping the version.

## Usage

1. Install the APK on a supported device (or build it as shown above).
2. Enable USB debugging and connect over ADB.
3. In the app, pick the install path:
   - **dirtyfrag (recommended)**: a single run - the chain deploys and
     late-loads the KernelSU module directly.
   - **Ghostlock chains**: the race-based exploit for the selected series;
     the device reboots during the run, and the app then performs the
     KernelSU late-load.

## Runtime options

The payloads read a small set of `GHOSTSAM_*` environment variables injected
by the app over ADB - for example `GHOSTSAM_NO_KSU`, `GHOSTSAM_KSU_DEFER`
and `GHOSTSAM_PARAMS_STRICT`. The authoritative list and semantics live in
the payload sources (`exploit/src/common/options.*`); the app UI exposes the
safe subset.

## Relationship to DFRoot

[DFRoot](https://github.com/diabl0w/DFRoot) by @diabl0w is a focused,
minimal implementation of the public DirtyFrag technique. GhostSam
integrates the earlier work (the Ghostlock exploit chains for S22 / S26 /
Z Fold8) into one toolkit, so the project is larger and more involved
overall - yet its dirtyfrag path is still fast: a single run deploys and
late-loads KernelSU. Give it a try; feedback, issues and PRs are welcome.

Both implementations build on the same public technique; see
[NOTICE](NOTICE) for the provenance statement.

## Disclaimer

This repository is published for security research and education only, and
targets devices you own and control. Running the Ghostlock chains reboots
the device, may leave SELinux permissive for the session (a Knox watchdog
may force a reboot), and can permanently brick a device if a wrong kernel
baseline is forced. You are responsible for your own device and for
complying with applicable law.

## Credits

- **s26 series** - merged from [ghostlock-s26](https://github.com/snothin/ghostlock-s26);
  its README carries the full credit list.
- **Z Fold8 transplant input (io_submit primer)** - from @diabl0w.
- **ksud / ksud-next** - built from [snothin/KernelSU](https://github.com/snothin/KernelSU)
  and [snothin/KernelSU-Next](https://github.com/snothin/KernelSU-Next), forks that
  track upstream and incorporate work from multiple developers in the KernelSU
  community.
- **dirtyfrag chain** - follows the public DirtyFrag technique; the provenance
  statement is in [NOTICE](NOTICE).

## License

Apache-2.0 - see [LICENSE](LICENSE). Third-party notices and
acknowledgements are in [NOTICE](NOTICE).
