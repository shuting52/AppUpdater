package com.appupdater.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appupdater.BuildConfig
import com.appupdater.update.AppInstaller
import com.appupdater.update.AppUpdateChecker
import com.appupdater.update.ApkDownloader
import com.appupdater.update.UpdateInfo
import com.appupdater.update.UpdateInstallReceiver
import com.appupdater.update.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 自动更新流程编排（独立于业务 App 的 ViewModel）：
 * 检测 → 弹窗（卡通动画）→ 下载 → 安装 → 完成/重试/权限引导。
 *
 * 对应原项目 MusicPlayerViewModel 中的更新流程部分，
 * 这里抽离成可独立复用的模块，任意 App 直接挂载即可。
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val appUpdateChecker = AppUpdateChecker(application)
    private val apkDownloader = ApkDownloader(application)
    private val appInstaller = AppInstaller(application)

    // ============ 自动更新状态 ============
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var latestUpdateInfo: UpdateInfo? = null
    private var downloadedApkFile: File? = null

    /** 是否已做过自动检查（每次启动只自动检查一次） */
    private var autoCheckDone = false

    init {
        // 订阅 PackageInstaller 安装结果，驱动弹窗状态流转
        viewModelScope.launch {
            UpdateInstallReceiver.Results.flow.collect { (success, message) ->
                if (success) {
                    _updateState.value = UpdateState.Done(installed = true)
                } else {
                    _updateState.value = UpdateState.Error(
                        message.ifBlank { "安装失败，请检查是否已开启「允许安装未知应用」权限" }
                    )
                }
            }
        }

        // 进入应用 1.5 秒后自动检测一次新版本
        viewModelScope.launch {
            delay(1500)
            checkForUpdate(manual = false)
        }
    }

    /** 检查新版本：manual=true 来自用户手动触发；false 为进入应用自动检查 */
    fun checkForUpdate(manual: Boolean) {
        if (_updateState.value is UpdateState.Checking) return
        if (!manual && autoCheckDone) return
        autoCheckDone = true

        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val latest = appUpdateChecker.fetchLatestInfo()
            if (latest != null && appUpdateChecker.hasNewVersion(latest)) {
                latestUpdateInfo = latest
                _updateState.value = UpdateState.Found(latest)
            } else {
                if (manual) {
                    _updateState.value = UpdateState.Error(
                        "当前已是最新版本 v${BuildConfig.VERSION_NAME} 🎉",
                        canRetry = false
                    )
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
        }
    }

    /**
     * 开始下载最新版 APK；
     * 【落地版】进度条走完 100% 后自动调起安装，无需再点「安装」按钮：
     *   下载完成 → 检查安装权限 → 有权限直接安装；无权限引导开启（开完可一键继续）。
     */
    fun startUpdateDownload() {
        val info = latestUpdateInfo ?: return
        if (_updateState.value is UpdateState.Downloading) return

        viewModelScope.launch {
            try {
                // 落地增强：若上次已下载完成的 APK 仍在且大小匹配，跳过下载直接安装（缓存续传）
                val cached = apkDownloader.targetFile()
                if (cached.exists() && (info.apkSize <= 0 || cached.length() == info.apkSize)) {
                    downloadedApkFile = cached
                    autoInstall()
                    return@launch
                }

                val file = apkDownloader.download(info.downloadUrl) { progress, downloaded, total ->
                    _updateState.value = UpdateState.Downloading(progress, downloaded, total)
                }
                downloadedApkFile = file
                // 下载完成后校验大小（可选，防传输损坏）
                if (info.apkSize > 0 && file.length() != info.apkSize) {
                    _updateState.value = UpdateState.Error(
                        "下载文件不完整（大小不匹配），请重试",
                        canRetry = true
                    )
                    return@launch
                }
                // 进度条结束 → 自动安装（不经过 DownloadReady 手动确认）
                autoInstall()
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(
                    "下载失败：${e.message ?: "网络异常"}",
                    canRetry = true
                )
            }
        }
    }

    /**
     * 自动安装新版本（自动替换旧版本）。
     * 未开启「允许安装未知应用」权限时进入引导态，用户开完权限后可再次调用（onInstall）。
     */
    fun installUpdate() {
        autoInstall()
    }

    private fun autoInstall() {
        val file = downloadedApkFile ?: return
        if (!appInstaller.canInstallUnknownApps()) {
            _updateState.value = UpdateState.NeedInstallPermission
            return
        }
        _updateState.value = UpdateState.Installing
        val launched = appInstaller.install(file)
        if (!launched) {
            _updateState.value = UpdateState.Error(
                "无法调起系统安装器，请检查是否已开启「允许安装未知应用」权限",
                canRetry = true
            )
        }
    }

    /** 重试（重新走一遍 检测 → 下载） */
    fun retryUpdate() {
        latestUpdateInfo?.let {
            _updateState.value = UpdateState.Found(it)
        } ?: checkForUpdate(manual = true)
    }

    /** 最新版本号（用于弹窗在下载/安装阶段持续展示） */
    val latestVersionName: String?
        get() = latestUpdateInfo?.versionName

    /** 打开系统「允许安装未知应用」设置页 */
    fun openInstallPermissionSettings() {
        appInstaller.openInstallPermissionSettings()
    }

    /** 关闭更新弹窗 */
    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * 安装完成后重启应用，让新版本真正生效（覆盖安装后旧进程可能仍在运行）。
     * 通过系统 Launcher Intent 冷启动自己，旧进程会自然结束。
     */
    fun restartApp() {
        dismissUpdate()
        val context = getApplication<Application>()
        try {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                Runtime.getRuntime().exit(0)
            }
        } catch (_: Exception) {
            // 重启失败时保持现状即可
        }
    }
}
