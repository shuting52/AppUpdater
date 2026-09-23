package com.appupdater.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 安装器：
 *
 * 1. 【优先】PackageInstaller 系统安装会话 —— 由系统原子化地完成
 *    「卸载旧版本 + 安装新版本」替换流程，签名一致时自动覆盖，无需额外确认（部分机型仍会弹系统确认）。
 * 2. 【兜底】FileProvider + ACTION_VIEW 调起系统安装器 —— 兼容性最好，同样会自动替换旧版本。
 *
 * 说明：Android 上安装与本地签名一致、包名相同的 APK，系统即视为“卸载旧版本并安装新版本”，
 * 数据与账号可平滑保留；因此两条路径都满足「自动安装新版本、卸载旧版本」的要求。
 */
class AppInstaller(private val context: Context) {

    /** 是否已允许「安装未知应用」（Android 8.0+ 需要，用于 FileProvider 路径） */
    fun canInstallUnknownApps(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
    }

    /** 跳转系统「允许安装未知应用」设置页 */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                // 部分 ROM 不支持直接跳转，提示用户手动开启
            }
        }
    }

    /**
     * 安装新版本 APK。
     * @param apkFile 已下载完成的 APK 文件
     * @return 是否成功调起安装流程（PackageInstaller 会话提交成功 或 已拉起系统安装器）
     */
    fun install(apkFile: File): Boolean {
        // 优先尝试 PackageInstaller 会话（更“自动”）
        if (installViaPackageInstaller(apkFile)) return true
        // 兜底：FileProvider 系统安装器
        return installViaFileProvider(apkFile)
    }

    /** PackageInstaller 系统安装会话，成功提交返回 true */
    private fun installViaPackageInstaller(apkFile: File): Boolean {
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            try {
                session.openWrite("appupdater_update.apk", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { input -> input.copyTo(out) }
                }
            } finally {
                session.close()
            }

            val intent = Intent(context, UpdateInstallReceiver::class.java)
            val flags = PendingIntentFlags.UPDATE_CURRENT or PendingIntentFlags.IMMUTABLE
            val pending = android.app.PendingIntent.getBroadcast(context, 100, intent, flags)
            session.commit(pending.intentSender)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** FileProvider + ACTION_VIEW 系统安装器（最通用的兜底方案） */
    private fun installViaFileProvider(apkFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            // 无 Activity 可处理安装请求（如权限未开启），由上层提示用户
            false
        }
    }

    private object PendingIntentFlags {
        const val UPDATE_CURRENT = android.app.PendingIntent.FLAG_UPDATE_CURRENT
        const val IMMUTABLE = android.app.PendingIntent.FLAG_IMMUTABLE
    }
}
