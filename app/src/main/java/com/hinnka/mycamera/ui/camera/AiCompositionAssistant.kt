package com.hinnka.mycamera.ui.camera

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.data.UserPreferences
import com.hinnka.mycamera.ui.prefs.rememberPreference
import com.hinnka.mycamera.ml.BlazeFaceDetector
import com.hinnka.mycamera.viewmodel.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private data class SceneReadout(
    val title: String,
    val icon: String,
    val advice: String,
    val skyRatio: Float,
    val greenRatio: Float,
    val darkRatio: Float,
)

/**
 * Fast on-device scene reading from a tiny preview frame. This is a lightweight visual heuristic,
 * not a downloaded semantic scene-classification neural network. It avoids adding a large model
 * or continuous GPU inference to mid-range phones.
 */
private object SceneCompositionAnalyzer {
    fun analyze(bitmap: Bitmap): SceneReadout {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 8 || h < 8) return SceneReadout("Đang phân tích", "◌", "Giữ máy ổn định", 0f, 0f, 0f)
        var samples = 0
        var sky = 0
        var green = 0
        var warm = 0
        var dark = 0
        var blueLower = 0
        var lowerSamples = 0
        // Sample a coarse grid only; this is cheap enough for periodic preview analysis.
        for (gy in 0 until 18) {
            val y = ((gy + 0.5f) * h / 18).toInt().coerceIn(0, h - 1)
            for (gx in 0 until 24) {
                val x = ((gx + 0.5f) * w / 24).toInt().coerceIn(0, w - 1)
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 255
                val g = (pixel shr 8) and 255
                val b = pixel and 255
                val brightness = (r * 0.2126f + g * 0.7152f + b * 0.0722f)
                samples++
                if (brightness < 48f) dark++
                if (y < h * 0.48f && b > 85 && b > r * 1.08f && b > g * 0.96f && brightness > 90f) sky++
                if (g > 62 && g > r * 1.12f && g > b * 1.08f) green++
                if (r > 135 && r > g * 1.12f && g > b * 1.18f) warm++
                if (y >= h * 0.55f) {
                    lowerSamples++
                    if (b > 75 && b > r * 1.08f && b > g * 0.92f) blueLower++
                }
            }
        }
        val skyRatio = sky.toFloat() / samples
        val greenRatio = green.toFloat() / samples
        val warmRatio = warm.toFloat() / samples
        val darkRatio = dark.toFloat() / samples
        val waterRatio = if (lowerSamples == 0) 0f else blueLower.toFloat() / lowerSamples
        return when {
            darkRatio > 0.48f -> SceneReadout("Cảnh đêm / thiếu sáng", "☾", "Giữ máy thật chắc · tránh zoom số quá cao", skyRatio, greenRatio, darkRatio)
            warmRatio > 0.16f && skyRatio > 0.08f -> SceneReadout("Hoàng hôn / ánh vàng", "☀", "Giữ vùng trời sáng · chạm lấy nét vào chủ thể", skyRatio, greenRatio, darkRatio)
            greenRatio > 0.22f -> SceneReadout("Thiên nhiên / cây xanh", "♧", "Đặt chủ thể lệch 1/3 · giữ màu xanh tự nhiên", skyRatio, greenRatio, darkRatio)
            waterRatio > 0.34f && skyRatio > 0.08f -> SceneReadout("Biển / mặt nước", "≈", "Giữ đường chân trời thẳng ở 1/3 khung hình", skyRatio, greenRatio, darkRatio)
            skyRatio > 0.20f -> SceneReadout("Phong cảnh / bầu trời", "⌁", if (skyRatio > 0.38f) "Hạ góc máy nhẹ để cân bằng trời và tiền cảnh" else "Đặt đường chân trời ở 1/3 khung hình", skyRatio, greenRatio, darkRatio)
            else -> SceneReadout("Cảnh tổng hợp", "✦", "Ưu tiên đường dẫn mắt · giữ chủ thể rõ và nền gọn", skyRatio, greenRatio, darkRatio)
        }
    }
}

@Composable
internal fun AiCompositionAssistant(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var enabled by rememberPreference(UserPreferences::aiCompositionEnabled)
    var faceBox by remember { mutableStateOf<RectF?>(null) }
    var scene by remember { mutableStateOf(SceneReadout("Đang nhận diện cảnh…", "✦", "Giữ máy ổn định", 0f, 0f, 0f)) }
    var guidance by remember { mutableStateOf("AI đang phân tích khung hình") }
    val detector = remember(context) {
        runCatching { BlazeFaceDetector(context.applicationContext, minScore = 0.65f) }.getOrNull()
    }

    DisposableEffect(detector) {
        onDispose { detector?.close() }
    }

    LaunchedEffect(enabled, detector, viewModel) {
        if (!enabled) {
            faceBox = null
            guidance = "AI Composition đã tắt"
            return@LaunchedEffect
        }
        while (isActive) {
            val surface = viewModel.glSurfaceView
            if (surface != null) {
                val frame = suspendCancellableCoroutine<Bitmap?> { continuation ->
                    surface.captureNextPreviewFrame(320) { bitmap ->
                        if (continuation.isActive) continuation.resume(bitmap)
                        else bitmap.recycle()
                    }
                }
                if (frame != null) {
                    try {
                        val analysis = withContext(Dispatchers.Default) {
                            val readout = SceneCompositionAnalyzer.analyze(frame)
                            val detections = detector?.detect(frame).orEmpty()
                            val subject = detections.maxByOrNull {
                                it.box.width().coerceAtLeast(0f) * it.box.height().coerceAtLeast(0f)
                            }
                            readout to subject?.box?.let { RectF(it) }
                        }
                        scene = analysis.first
                        faceBox = analysis.second
                        guidance = if (analysis.second != null) {
                            "Đã tìm thấy người · căn theo điểm 1/3"
                        } else {
                            analysis.first.advice
                        }
                    } catch (_: Exception) {
                        guidance = "Đang tối ưu bố cục…"
                    } finally {
                        if (!frame.isRecycled) frame.recycle()
                    }
                }
            }
            delay(1100)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (enabled) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val lineColor = Color.White.copy(alpha = 0.34f)
                // Rule-of-thirds grid with subtle center crosshair.
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(w / 3f, 0f), androidx.compose.ui.geometry.Offset(w / 3f, h), 1.dp.toPx())
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(w * 2f / 3f, 0f), androidx.compose.ui.geometry.Offset(w * 2f / 3f, h), 1.dp.toPx())
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, h / 3f), androidx.compose.ui.geometry.Offset(w, h / 3f), 1.dp.toPx())
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, h * 2f / 3f), androidx.compose.ui.geometry.Offset(w, h * 2f / 3f), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.20f), androidx.compose.ui.geometry.Offset(w / 2f - 7.dp.toPx(), h / 2f), androidx.compose.ui.geometry.Offset(w / 2f + 7.dp.toPx(), h / 2f), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.20f), androidx.compose.ui.geometry.Offset(w / 2f, h / 2f - 7.dp.toPx()), androidx.compose.ui.geometry.Offset(w / 2f, h / 2f + 7.dp.toPx()), 1.dp.toPx())
                faceBox?.let { box ->
                    drawRect(
                        color = Color(0xFF7EF0B2),
                        topLeft = androidx.compose.ui.geometry.Offset(box.left * w, box.top * h),
                        size = androidx.compose.ui.geometry.Size(box.width() * w, box.height() * h),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(scene.icon, color = Color(0xFFB8F3D1), fontSize = 17.sp)
                    Column {
                        Text(scene.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("AI SCENE ANALYSIS", color = Color(0xFFB8F3D1), fontSize = 8.sp, letterSpacing = 1.2.sp)
                    }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.76f),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("GỢI Ý BỐ CỤC", color = Color(0xFF8FE9B8), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(guidance, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Căn theo lưới 1/3 · giữ đường chân trời thẳng", color = Color.White.copy(alpha = 0.68f), fontSize = 9.sp)
                }
            }
        }
        Surface(
            color = if (enabled) Color(0xFF155C43).copy(alpha = 0.94f) else Color.Black.copy(alpha = 0.68f),
            contentColor = Color.White,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clickable {
                enabled = !enabled
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✦", color = Color(0xFFB8F3D1), fontSize = 13.sp)
                Text(if (enabled) "AI COMPOSITION · ON" else "AI COMPOSITION · OFF", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
            }
        }
    }
}
