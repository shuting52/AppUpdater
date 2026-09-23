package com.appupdater.update

import org.json.JSONObject

/**
 * 更新信息模型 —— 对应远端 update.json 的字段结构。
 *
 * 远端配置示例：
 * {
 *   "versionCode": 2,
 *   "versionName": "1.0.1",
 *   "downloadUrl": "https://example.com/app-1.0.1.apk",
 *   "apkSize": 26843546,
 *   "releaseNotes": ["新增自动更新", "优化体验"],
 *   "forceUpdate": false,
 *   "md5": ""
 * }
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val apkSize: Long,
    val releaseNotes: List<String>,
    val forceUpdate: Boolean = false,
    val md5: String? = null,
    /** 是否为内置演示配置（用于无网络 / 演示环境预览完整更新流程） */
    val isDemo: Boolean = false
) {
    companion object {
        fun fromJson(json: JSONObject, isDemo: Boolean = false): UpdateInfo? {
            return try {
                val notes = json.optJSONArray("releaseNotes")
                val noteList = if (notes != null) {
                    (0 until notes.length()).map { notes.optString(it) }
                } else {
                    listOf("优化使用体验，修复已知问题")
                }
                UpdateInfo(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName", ""),
                    downloadUrl = json.optString("downloadUrl", ""),
                    apkSize = json.optLong("apkSize", 0L),
                    releaseNotes = noteList,
                    forceUpdate = json.optBoolean("forceUpdate", false),
                    md5 = json.optString("md5").ifBlank { null },
                    isDemo = isDemo
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * 更新流程状态机 —— ViewModel 持有，弹窗根据状态渲染不同界面。
 */
sealed class UpdateState {
    /** 初始空闲态 */
    object Idle : UpdateState()

    /** 正在检测新版本 */
    object Checking : UpdateState()

    /** 检测到新版本，等待用户确认更新 */
    data class Found(val info: UpdateInfo) : UpdateState()

    /** 正在下载 APK */
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateState()

    /** APK 已下载完毕，等待安装 */
    object DownloadReady : UpdateState()

    /** 正在安装（系统安装会话提交中） */
    object Installing : UpdateState()

    /** 需要用户先开启「允许安装未知应用」权限 */
    object NeedInstallPermission : UpdateState()

    /** 更新成功（PackageInstaller 路径下应用未被杀死时可见） */
    data class Done(val installed: Boolean) : UpdateState()

    /** 出错（下载失败 / 安装失败 / 已是最新） */
    data class Error(val message: String, val canRetry: Boolean = true) : UpdateState()
}
