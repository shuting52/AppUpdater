package com.appupdater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appupdater.ui.components.CartoonUpdateDialog
import com.appupdater.update.UpdateState
import com.appupdater.viewmodel.UpdateViewModel

/**
 * 独立「卡通自动更新」演示宿主：
 * 进入应用 1.5 秒后自动检测一次（默认走内置 update_demo.json 演示配置），
 * 也可点击下方按钮手动检查更新。
 *
 * 接入你自己的 App 时，只需：
 *   1. 拷贝 update/ 包 + ui/components/CartoonUpdateDialog.kt；
 *   2. 用 UpdateViewModel 编排流程；
 *   3. 在任意界面按本 Activity 底部的方式挂载弹窗。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppUpdaterTheme {
                val updateState by viewModel.updateState.collectAsStateWithLifecycle()

                DemoHome(
                    versionName = BuildConfig.VERSION_NAME,
                    onCheckUpdate = { viewModel.checkForUpdate(manual = true) }
                )

                // 挂载动态卡通更新弹窗（覆盖所有界面，自动/手动触发）
                if (updateState !is UpdateState.Idle && updateState !is UpdateState.Checking) {
                    CartoonUpdateDialog(
                        state = updateState,
                        currentVersion = BuildConfig.VERSION_NAME,
                        newVersion = viewModel.latestVersionName,
                        onStartDownload = { viewModel.startUpdateDownload() },
                        onInstall = { viewModel.installUpdate() },
                        onOpenInstallSettings = { viewModel.openInstallPermissionSettings() },
                        onDismiss = { viewModel.dismissUpdate() },
                        onRetry = { viewModel.retryUpdate() },
                        onDone = { viewModel.dismissUpdate() },
                        onRestartApp = { viewModel.restartApp() }
                    )
                }
            }
        }
    }
}

// 弹窗同款霓虹卡通风配色
private val CuteCyan = Color(0xFF4DE3FF)
private val CutePurple = Color(0xFF8B5CF6)
private val CutePink = Color(0xFFFF5FA2)

@Composable
fun AppUpdaterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CuteCyan,
            secondary = CutePurple,
            tertiary = CutePink,
            background = Color(0xFF16151F),
            surface = Color(0xFF1E1D2B)
        ),
        content = content
    )
}

@Composable
private fun DemoHome(versionName: String, onCheckUpdate: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 徽章
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(CuteCyan, CutePurple, CutePink))),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "↻", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "卡通自动更新 · 独立演示",
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "检测 → 卡通弹窗 → 下载 → 安装",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(26.dp))

            // 当前版本
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "当前版本",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v$versionName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 手动检查更新按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(CuteCyan, CutePurple, CutePink)))
                    .clickable(onClick = onCheckUpdate)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "检查更新",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "进入应用 1.5 秒后会自动检测一次新版本",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.height(36.dp))

            // 状态说明
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("演示说明", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CuteCyan)
                Text(
                    text = "默认读取内置 update_demo.json，可直接预览完整更新动画。\n" +
                            "构建时用 UPDATE_CHECK_URL 注入远端清单、并发布真实 APK 后，即可体验真实下载与覆盖安装。",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
