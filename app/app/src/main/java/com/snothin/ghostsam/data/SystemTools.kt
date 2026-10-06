package com.snothin.ghostsam.data

import com.snothin.ghostsam.R

enum class SystemToolKind {
    COMMAND,

    TERMINAL,
}

data class SystemTool(
    val titleRes: Int,
    val summaryRes: Int,
    val kind: SystemToolKind,
    val command: String = "",
    /** Existence check: usable = non-empty output, no channel sentinel, not starting with "Error". */
    val checkCommand: String? = null,
)

private const val HIDDEN_EXTRA = " -e 7267864872 72678647376477466"

private fun checkActivity(component: String) =
    "cmd package resolve-activity --brief -n $component"

private fun checkPackage(pkg: String) = "pm path $pkg"

val SystemTools: List<SystemTool> = listOf(
    SystemTool(
        R.string.tools_terminal,
        R.string.tools_terminal_summary,
        SystemToolKind.TERMINAL,
    ),
    SystemTool(
        R.string.tools_shell_identity,
        R.string.tools_shell_identity_summary,
        SystemToolKind.COMMAND,
        command = "id && uname -a",
    ),
    SystemTool(
        R.string.tools_band_selection,
        R.string.tools_band_selection_summary,
        SystemToolKind.COMMAND,
        command = "am start com.samsung.android.app.telephonyui/.hiddennetworksetting.MainActivity",
        checkCommand = checkActivity("com.samsung.android.app.telephonyui/.hiddennetworksetting.MainActivity"),
    ),
    SystemTool(
        R.string.tools_service_mode,
        R.string.tools_service_mode_summary,
        SystemToolKind.COMMAND,
        command = "am broadcast -a com.samsung.android.action.SECRET_CODE " +
            "-d android_secret_code://2263 " +
            "-n com.sec.android.RilServiceModeApp/.SecKeyStringBroadcastReceiver",
        checkCommand = checkPackage("com.sec.android.RilServiceModeApp"),
    ),
    SystemTool(
        R.string.tools_preconfig,
        R.string.tools_preconfig_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.samsung.android.cidmanager/.modules.preconfig.PreconfigActivity " +
            "-a com.samsung.android.action.SECRET_CODE -d secret_code://27262826 --ei type 2",
        checkCommand = checkActivity("com.samsung.android.cidmanager/.modules.preconfig.PreconfigActivity"),
    ),
    SystemTool(
        R.string.tools_usb_settings,
        R.string.tools_usb_settings_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.sec.usbsettings/.USBSettings",
        checkCommand = checkActivity("com.sec.usbsettings/.USBSettings"),
    ),
    SystemTool(
        R.string.tools_ims_settings,
        R.string.tools_ims_settings_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.samsung.advp.imssettings/.MainActivity",
        checkCommand = checkActivity("com.samsung.advp.imssettings/.MainActivity"),
    ),
    SystemTool(
        R.string.tools_parser_secret_code,
        R.string.tools_parser_secret_code_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.sec.android.app.parser/.SecretCodeIME",
        checkCommand = checkActivity("com.sec.android.app.parser/.SecretCodeIME"),
    ),
    SystemTool(
        R.string.tools_sideload,
        R.string.tools_sideload_summary,
        SystemToolKind.COMMAND,
        command = "am broadcast -n com.wssyncmldm/com.idm.fotaagent.receiver.SecretCodeReceiver",
        checkCommand = checkPackage("com.wssyncmldm"),
    ),
)

val SystemToolsLegacy: List<SystemTool> = listOf(
    SystemTool(
        R.string.tools_band_priority,
        R.string.tools_band_priority_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.sec.hiddenmenu/.BandPriorityEdit$HIDDEN_EXTRA",
        checkCommand = checkActivity("com.sec.hiddenmenu/.BandPriorityEdit"),
    ),
    SystemTool(
        R.string.tools_global_hidden,
        R.string.tools_global_hidden_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.sec.hiddenmenu/.GlobalHiddenMenuEnable$HIDDEN_EXTRA",
        checkCommand = checkActivity("com.sec.hiddenmenu/.GlobalHiddenMenuEnable"),
    ),
    SystemTool(
        R.string.tools_field_test,
        R.string.tools_field_test_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.sec.hiddenmenu/.FIELDTESTMODE$HIDDEN_EXTRA",
        checkCommand = checkActivity("com.sec.hiddenmenu/.FIELDTESTMODE"),
    ),
    SystemTool(
        R.string.tools_debug_menu,
        R.string.tools_debug_menu_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.sec.hiddenmenu/.DEBUGMENU$HIDDEN_EXTRA",
        checkCommand = checkActivity("com.sec.hiddenmenu/.DEBUGMENU"),
    ),
    SystemTool(
        R.string.tools_dm_mode,
        R.string.tools_dm_mode_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.sec.hiddenmenu/.DmMode$HIDDEN_EXTRA",
        checkCommand = checkActivity("com.sec.hiddenmenu/.DmMode"),
    ),
    SystemTool(
        R.string.tools_regional_mode,
        R.string.tools_regional_mode_summary,
        SystemToolKind.COMMAND,
        command = "am start -n com.sec.hiddenmenu/.KOREA_Mode$HIDDEN_EXTRA",
        checkCommand = checkActivity("com.sec.hiddenmenu/.KOREA_Mode"),
    ),
)
