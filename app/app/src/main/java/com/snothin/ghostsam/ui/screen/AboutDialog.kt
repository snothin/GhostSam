package com.snothin.ghostsam.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snothin.ghostsam.BuildConfig
import com.snothin.ghostsam.R
import com.snothin.ghostsam.data.exploit.RunStatsStore
import com.snothin.ghostsam.data.prefs.PayloadProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var stats by remember { mutableStateOf<AboutStats?>(null) }
    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) {
            val profiles = PayloadProfileStore(context).load()
            val profileOk = profiles.sumOf { it.successCount }
            val profileFail = profiles.sumOf { it.failCount }
            AboutStats(
                total = RunStatsStore.builtinRun(context) + profiles.sumOf { it.runCount },
                builtinOk = RunStatsStore.builtinOk(context),
                builtinFail = RunStatsStore.builtinFail(context),
                profileOk = profileOk,
                profileFail = profileFail,
            )
        }
    }
    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_title),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = MiuixTheme.textStyles.title4.fontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.about_body),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.home_version_format,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        BuildTypeBadge()
                    }
                    stats?.let { s ->
                        Text(
                            text = stringResource(R.string.about_stats_total_builtin, s.total, s.builtinOk, s.builtinFail),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.85f),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.about_credit, CREDIT_AUTHOR, CREDIT_DATE),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.7f),
                    )
                    stats?.let { s ->
                        Text(
                            text = stringResource(R.string.about_stats_profiles, s.profileOk, s.profileFail),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.85f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 0.5.dp,
                    color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f),
                )
                Spacer(Modifier.height(6.dp))
                AboutLinkRow(
                    icon = painterResource(R.drawable.ic_kernelsu),
                    title = stringResource(R.string.about_ksu_title),
                    description = stringResource(R.string.about_ksu_description),
                    onClick = { uriHandler.openUri(KERNEL_SU_URL) },
                )
                AboutLinkRow(
                    icon = painterResource(R.drawable.ic_kernelsu),
                    title = stringResource(R.string.about_ksunext_title),
                    description = stringResource(R.string.about_ksunext_description),
                    onClick = { uriHandler.openUri(KERNEL_SU_NEXT_URL) },
                )
                AboutLinkRow(
                    icon = painterResource(R.drawable.ic_github),
                    title = stringResource(R.string.about_s26_title),
                    description = stringResource(R.string.about_s26_description),
                    onClick = { uriHandler.openUri(GHOSTLOCK_S26_URL) },
                )
                AboutLinkRow(
                    icon = painterResource(R.drawable.ic_github),
                    title = stringResource(R.string.about_app_title),
                    description = stringResource(R.string.about_app_description),
                    onClick = { uriHandler.openUri(GHOSTSAM_APP_URL) },
                )
                AboutLinkRow(
                    icon = painterResource(R.drawable.ic_github),
                    title = stringResource(R.string.about_dfreroot_title),
                    description = stringResource(R.string.about_dfreroot_description),
                    onClick = { uriHandler.openUri(DFREROOT_URL) },
                )
                AboutLinkRow(
                    icon = painterResource(R.drawable.ic_github),
                    title = stringResource(R.string.about_dfrroot_title),
                    description = stringResource(R.string.about_dfrroot_description),
                    onClick = { uriHandler.openUri(DFRROOT_URL) },
                )
                Spacer(Modifier.height(10.dp))
                TextButton(
                    text = stringResource(R.string.about_close),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun AboutLinkRow(
    icon: Painter,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.Link,
            contentDescription = stringResource(R.string.open_github),
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun BuildTypeBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = BuildConfig.BUILD_TYPE,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private const val CREDIT_AUTHOR = "nothin"
private const val CREDIT_DATE = "2026.10.02"
private const val KERNEL_SU_URL = "https://github.com/tiann/KernelSU"
private const val KERNEL_SU_NEXT_URL = "https://github.com/KernelSU-Next/KernelSU-Next"
private const val GHOSTLOCK_S26_URL = "https://github.com/snothin/ghostlock-s26"
private const val GHOSTSAM_APP_URL = "https://github.com/snothin/GhostSam"
private const val DFREROOT_URL = "https://github.com/polygraphene/DFReroot"
private const val DFRROOT_URL = "https://github.com/diabl0w/DFRoot"

private data class AboutStats(
    val total: Int,
    val builtinOk: Int,
    val builtinFail: Int,
    val profileOk: Int,
    val profileFail: Int,
)
