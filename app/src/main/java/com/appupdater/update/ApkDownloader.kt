package com.appupdater.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * APK 下载器：流式写入应用专属目录（无需存储权限），
 * 通过 onProgress 回调实时上报下载进度。
 */
class ApkDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 下载目标文件（应用外部专属目录，FileProvider 可正常分享给系统安装器） */
    fun targetFile(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "appupdater_update.apk")

    /**
     * 下载 APK 到本地。
     * @param url          APK 下载地址
     * @param onProgress   (进度 0..1, 已下载字节, 总字节)
     * @return 下载完成的文件；失败抛出 IOException
     */
    suspend fun download(url: String, onProgress: (Float, Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (url.isBlank() || !url.startsWith("http")) {
                throw IOException("下载地址无效")
            }
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("下载失败：HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IOException("响应体为空")
                val total = body.contentLength()
                val target = targetFile()
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            val progress = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            onProgress(progress, downloaded, total)
                        }
                    }
                }
                if (downloaded <= 0L) {
                    throw IOException("下载文件为空")
                }
                target
            }
        }
}
