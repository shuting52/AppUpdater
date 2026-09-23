package com.appupdater.update

import android.content.Context
import com.appupdater.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 版本检测器：
 * 1. 优先从 BuildConfig.UPDATE_CHECK_URL 拉取远端 update.json（生产环境配置）；
 * 2. 远端不可达时回退到内置 assets/update_demo.json 演示配置，
 *    方便在没有真实发布服务器的情况下完整预览「检测 → 弹窗 → 下载 → 安装」流程。
 *
 * 判定逻辑：远端 versionCode > 本地 versionCode 即视为有新版本。
 * （versionName 作为展示文本，如 1.0.0 → 1.0.1）
 */
class AppUpdateChecker(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 拉取并解析最新版本信息；解析失败返回 null（交由上层决定提示文案） */
    suspend fun fetchLatestInfo(): UpdateInfo? = withContext(Dispatchers.IO) {
        val remoteUrl = BuildConfig.UPDATE_CHECK_URL
        if (remoteUrl.isNotBlank() && remoteUrl.startsWith("http")) {
            try {
                val request = Request.Builder().url(remoteUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val info = UpdateInfo.fromJson(JSONObject(body), isDemo = false)
                            if (info != null) return@withContext info
                        }
                    }
                }
            } catch (_: Exception) {
                // 网络异常，走演示配置
            }
        }

        // 兜底：内置演示配置（标记 isDemo = true，弹窗会展示「演示模式」角标）
        try {
            val text = context.assets.open("update_demo.json").bufferedReader().use { it.readText() }
            UpdateInfo.fromJson(JSONObject(text), isDemo = true)
        } catch (_: Exception) {
            null
        }
    }

    /** 是否有新版本：远端 versionCode > 本地 versionCode */
    fun hasNewVersion(latest: UpdateInfo, currentCode: Int = BuildConfig.VERSION_CODE): Boolean {
        return latest.versionCode > currentCode
    }

    /** 格式化文件大小（B/KB/MB） */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "--"
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1) {
            String.format("%.1f MB", mb)
        } else {
            String.format("%.0f KB", bytes / 1024.0)
        }
    }
}
