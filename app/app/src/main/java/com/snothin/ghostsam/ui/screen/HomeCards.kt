package com.snothin.ghostsam.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.device.DeviceSnapshot
import com.snothin.ghostsam.data.channel.SelinuxState
import com.snothin.ghostsam.data.device.SeriesMatch
import com.snothin.ghostsam.data.device.SeriesTable
import com.snothin.ghostsam.data.prefs.SettingsPrefs
import com.snothin.ghostsam.ui.theme.KsudCardBlue
import com.snothin.ghostsam.ui.theme.NatsumeGray
import com.snothin.ghostsam.ui.theme.StatusColors
import com.snothin.ghostsam.ui.theme.SuccessAccent
import com.snothin.ghostsam.ui.theme.SuDeniedCardAccent
import com.snothin.ghostsam.ui.theme.SuDeniedCardInk
import com.snothin.ghostsam.ui.theme.SuDeniedCardLight
import com.snothin.ghostsam.ui.theme.WarningAccent
import com.snothin.ghostsam.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

// Home status: 7 states; Persisted = DFR persist form, Standalone = no-precondition form.
enum class SummonStatus {
    Ready,
    Working,
    Persisted,
    Standalone,
    ManagerMissing,
    SuDenied,
    Disconnected,
}

private data class SummonStatusUi(
    val titleRes: Int,
    val detailRes: Int,
    val icon: ImageVector,
    val colors: StatusColors,
    val decoration: Color,
    val detailOverride: String? = null,
    val titleOverride: String? = null,
)

@Composable
private fun ksuManagerLabel(): String =
    SettingsPrefs.effectiveKsudVariant(LocalContext.current).label

@Composable
private fun summonStatusUi(
    status: SummonStatus,
    isDark: Boolean,
    disconnectedDetailRes: Int,
    disconnectedDetailOverride: String?,
    workingDetailOverride: String?,
): SummonStatusUi = when (status) {
    SummonStatus.Ready -> SummonStatusUi(
        R.string.home_status_ksu_enabled,
        R.string.home_status_ksu_enabled_detail,
        Icons.Rounded.CheckCircleOutline,
        StatusColors.success(isDark),
        SuccessAccent,
        titleOverride = stringResource(R.string.home_status_ksu_enabled, ksuManagerLabel()),
    )

    SummonStatus.Working -> SummonStatusUi(
        R.string.home_status_working,
        R.string.home_status_working_detail,
        Icons.Rounded.AutoAwesome,
        StatusColors(
            MiuixTheme.colorScheme.primaryContainer,
            MiuixTheme.colorScheme.onPrimaryContainer,
        ),
        MiuixTheme.colorScheme.primary.copy(alpha = 0.8f),
        detailOverride = workingDetailOverride,
    )

    SummonStatus.Persisted -> SummonStatusUi(
        R.string.home_status_persist,
        R.string.home_status_persist_detail,
        Icons.Rounded.AdminPanelSettings,
        StatusColors.success(isDark),
        SuccessAccent,
    )

    SummonStatus.Standalone -> SummonStatusUi(
        R.string.home_status_standalone,
        R.string.home_status_standalone_detail,
        Icons.Rounded.Security,
        StatusColors.success(isDark),
        SuccessAccent,
    )

    SummonStatus.ManagerMissing -> SummonStatusUi(
        R.string.home_status_manager_missing,
        R.string.home_status_manager_missing_detail,
        Icons.Rounded.Info,
        StatusColors.warning(isDark),
        WarningAccent,
        titleOverride = stringResource(R.string.home_status_manager_missing, ksuManagerLabel()),
        detailOverride = stringResource(R.string.home_status_manager_missing_detail, ksuManagerLabel()),
    )

    SummonStatus.SuDenied -> SummonStatusUi(
        R.string.home_status_su_denied,
        R.string.home_status_su_denied_detail,
        Icons.Rounded.Info,
        StatusColors.warning(isDark),
        WarningAccent,

    )

    SummonStatus.Disconnected -> SummonStatusUi(
        R.string.home_status_disconnected,
        disconnectedDetailRes,
        Icons.Rounded.LinkOff,
        StatusColors.warning(isDark),
        WarningAccent,
        detailOverride = disconnectedDetailOverride,
    )
}

@Composable
internal fun KsudUpgradeCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Tilt,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(KsudCardBlue),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = Color.White,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ksud_load_card_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.ksud_load_card_detail),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
internal fun SuDeniedConnectCard(
    onClick: () -> Unit,
    showAction: Boolean = true,
    hintRes: Int = R.string.su_denied_connect_hint,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = showAction,
        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Tilt,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SuDeniedCardLight)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = SuDeniedCardAccent,
            )
            Text(
                text = stringResource(hintRes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = SuDeniedCardInk,
                modifier = Modifier.weight(1f),
            )
            if (showAction) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = SuDeniedCardAccent,
                )
            }
        }
    }
}

@Composable
internal fun SummonStatusCard(
    status: SummonStatus,
    ksuActive: Boolean,
    noKsu: Boolean = false,
    rootInfo: String? = null,
    useShizuku: Boolean = false,
    channelConnected: Boolean,
    disconnectedDetailRes: Int,
    disconnectedDetailOverride: String? = null,
    workingDetailOverride: String? = null,
    persistHostLine: String? = null,
    standaloneRoute: Boolean = false,
    nextVariant: Boolean = false,
    onClick: () -> Unit,
) {
    val isDark = isInDarkTheme()
    // Ready has three forms: KSU inactive (runnable) / KSU active (enabled) /
    // no-ksu active (temporary root enabled).
    val ui = when {
        status == SummonStatus.Ready && !ksuActive -> {
            SummonStatusUi(
                R.string.home_status_ready,
                R.string.home_status_ready_detail,
                Icons.Rounded.Bolt,
                StatusColors.success(isDark),
                SuccessAccent,
            )
        }
        status == SummonStatus.Ready && ksuActive && noKsu -> {
            SummonStatusUi(
                R.string.home_status_noksu_enabled,
                R.string.home_status_noksu_enabled_detail,
                Icons.Rounded.Bolt,
                StatusColors.success(isDark),
                SuccessAccent,
            )
        }
        else -> summonStatusUi(
            status, isDark, disconnectedDetailRes, disconnectedDetailOverride,
            workingDetailOverride,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(105.dp)
                .background(ui.colors.container),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    modifier = Modifier.size(130.dp),
                    imageVector = ui.icon,
                    tint = ui.decoration,
                    contentDescription = null,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp, 16.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Column {
                    Text(
                        text = ui.titleOverride ?: stringResource(ui.titleRes),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ui.colors.content,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = ui.detailOverride ?: stringResource(ui.detailRes),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = ui.colors.content.copy(alpha = 0.7f),
                    )
                }
            }
            if (ksuActive && !rootInfo.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 10.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        text = rootInfo,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ui.colors.content.copy(alpha = 0.8f),
                    )
                }
            } else if (status == SummonStatus.Persisted && persistHostLine != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 10.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = ui.colors.content.copy(alpha = 0.8f),
                        )
                        Text(
                            text = persistHostLine,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ui.colors.content.copy(alpha = 0.8f),
                        )
                    }
                }
            } else if (standaloneRoute || status == SummonStatus.Standalone) {
                if (nextVariant) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp, 10.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(
                            text = stringResource(R.string.home_status_next_spoofed_hint),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ui.colors.content.copy(alpha = 0.8f),
                        )
                    }
                }
            } else if (!(status == SummonStatus.Ready && ksuActive)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 10.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        if (channelConnected && useShizuku) {
                            Icon(
                                painter = painterResource(R.drawable.ic_shizuku),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = ui.colors.content.copy(alpha = 1f),
                            )
                        } else {
                            Icon(
                                imageVector = if (channelConnected) Icons.Rounded.Wifi else Icons.Rounded.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = ui.colors.content.copy(alpha = if (channelConnected) 1f else 0.7f),
                            )
                        }
                        Text(
                            text = stringResource(
                                when {
                                    useShizuku -> R.string.advanced_shizuku
                                    else -> R.string.adb_channel
                                },
                            ) + " · " +
                                stringResource(
                                    when {
                                        !channelConnected -> R.string.advanced_not_connected
                                        else -> R.string.shizuku_connected
                                    },
                                ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ui.colors.content.copy(alpha = if (channelConnected) 1f else 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeviceInfoCard(
    device: DeviceSnapshot,
    baseband: String,
    oneUi: String,
    seriesMatch: SeriesMatch,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoRow(
                icon = Icons.Rounded.Smartphone,
                label = stringResource(R.string.device_model),
                value = "${device.manufacturer} ${device.model}",
            )
            InfoRow(
                icon = Icons.Rounded.Code,
                label = stringResource(R.string.device_codename),
                value = device.device,
            )
            val series = seriesMatch.series
            if (series != null) {
                InfoRow(
                    icon = Icons.Rounded.Layers,
                    label = stringResource(R.string.device_series),
                    value = seriesValue(series, seriesMatch.kmiOk),
                    badge = series.takeIf { it.experimental }
                        ?.let { stringResource(R.string.device_series_experimental) },
                )
            }
            if (device.manufacturer.equals("samsung", ignoreCase = true)) {
                InfoRow(
                    icon = Icons.Rounded.Palette,
                    label = stringResource(R.string.device_oneui),
                    value = oneUi.ifBlank { stringResource(R.string.home_unknown) },
                )
            }
            InfoRow(
                icon = Icons.Rounded.SettingsInputAntenna,
                label = stringResource(R.string.baseband_version),
                value = baseband.ifBlank { stringResource(R.string.home_unknown) },
            )
            InfoRow(
                icon = Icons.Rounded.Terminal,
                label = stringResource(R.string.kernel_version),
                value = device.kernelVersion,
            )
            InfoRow(
                icon = Icons.Rounded.Fingerprint,
                label = stringResource(R.string.system_fingerprint),
                value = device.fingerprint,
            )
            InfoRow(
                icon = Icons.Rounded.Security,
                label = stringResource(R.string.system_abi),
                value = device.abi,
            )
        }
    }
}

@Composable
private fun seriesValue(series: SeriesTable.Series, kmiOk: Boolean): String {
    val base = "${series.id} · ${stringResource(R.string.device_series_pack_bundled)}"
    return if (kmiOk) {
        base
    } else {
        "$base · " + stringResource(R.string.device_series_kmi_mismatch, series.kmi)
    }
}

@Composable
internal fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    badge: String? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = NatsumeGray,
        )
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                MiuixTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = badge,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }
            Text(
                text = value,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

@Composable
internal fun selinuxLabel(state: SelinuxState): String = when (state) {
    SelinuxState.Enforcing -> stringResource(R.string.advanced_selinux_enforcing)
    SelinuxState.Permissive -> stringResource(R.string.selinux_permissive)
    SelinuxState.Unknown -> stringResource(R.string.selinux_unknown)
}

@Composable
internal fun StatusSummaryCard(
    selinuxState: SelinuxState,
    onClick: () -> Unit,
) {
    val stateColor = when (selinuxState) {
        SelinuxState.Enforcing -> SuccessAccent
        SelinuxState.Permissive -> WarningAccent
        SelinuxState.Unknown -> MiuixTheme.colorScheme.onBackgroundVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = true,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = NatsumeGray,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.advanced_selinux),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.selinux_status_description),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                Text(
                    text = selinuxLabel(selinuxState),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = stateColor,
                )
            }
        }
    }
}

@Composable
internal fun PayloadConfigCard(
    profileCount: Int,
    successCount: Int,
    onClick: () -> Unit,
) {
    val isDark = isInDarkTheme()
    val colors = StatusColors.beanYellow(isDark)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.container)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = colors.content,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.payload_card_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.content,
                )
                Text(
                    text = stringResource(R.string.payload_card_summary, profileCount, successCount),
                    fontSize = 13.sp,
                    color = colors.content.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colors.content.copy(alpha = 0.7f),
            )
        }
    }
}
