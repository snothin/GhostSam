package com.snothin.ghostsam.data

/** App-side runtime contract (device paths / env names); keep in sync with exploit/src/common/const.h. */
object Contract {

    const val BOOT_LOG = "/data/local/tmp/ghostsam-boot.log"

    const val LEGACY_BOOT_LOG = "/data/local/tmp/ghostlock-boot.log"

    const val RUN_LOG = "/data/local/tmp/ghostsam-run.log"

    const val LEGACY_RUN_LOG = "/data/local/tmp/ghostlock-run.log"

    const val LINES_CONF = "/data/local/tmp/ghostsam-lines.conf"

    const val LEGACY_LINES_CONF = "/data/local/tmp/ghostlock-lines.conf"

    const val CONF = "/data/local/tmp/ghostsam.conf"

    const val LEGACY_CONF = "/data/local/tmp/ghostlock.conf"

    const val PRELOAD = "/data/local/tmp/preload.so"

    const val PRELOAD_S22 = "/data/local/tmp/cve-2026-43499"

    const val ROOT_HELPER = "/data/local/tmp/cve-2026-43499-root"

    const val KSUD = "/data/local/tmp/ksud"

    const val KSUD_STAGE = "/data/local/tmp/.ksud-stage"

    const val SOCKET = "/data/local/tmp/temp_su.sock"

    const val DFR_DIR = "/data/local/tmp/dirtyfrag"

    const val DFR_PAYLOAD = "$DFR_DIR/dfr_payload"

    const val DFR_LKM = "$DFR_DIR/dfr_lkm.ko"

    const val DFR_STATUS = "$DFR_DIR/dfr-payload.status"

    /** Exec-disguise magic `@exec-disguise <src> [args...]`: the daemon binds src over /system/bin/logcat
     *  in a private mount ns (DEFEX checks the path only; a direct exec from /data is killed). */
    const val EXEC_DISGUISE = "@exec-disguise"

    /** dfr_lkm.ko staged in /dev (root reads of /data are DEFEX-blocked); /dev is wiped on reboot,
     *  so it stays out of STALE_FILES. */
    const val DFR_LKM_DEV = "/dev/.dfr-lkm.ko"

    const val ENV_NO_KSU = "GHOSTSAM_NO_KSU"

    const val LEGACY_ENV_NO_KSU = "GHOSTLOCK_NO_KSU"

    /** Paths removed before staging (`rm -f`): stale same-size files bypass the write-size cache;
     *  leftover status/env can be mistaken for this run's result. */
    val STALE_FILES = listOf(
        PRELOAD,
        PRELOAD_S22,
        KSUD,
        ROOT_HELPER,
        LINES_CONF,
        LEGACY_LINES_CONF,
        CONF,
        LEGACY_CONF,
        DFR_PAYLOAD,
        DFR_LKM,
        DFR_STATUS,
        BOOT_LOG,
        LEGACY_BOOT_LOG,
    )
}
