package com.hinnka.mycamera.ui.camera

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.hinnka.mycamera.ml.BlazeFaceDetector
import com.hinnka.mycamera.viewmodel.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs

@Composable
internal fun AiCompositionAssistant(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    var faceBox by remember { mutableStateOf<RectF?>(null) }
    var guidance by remember { mutableStateOf("Đang tìm khuôn mặt…") }
    val detector = remember(context) {
        runCatching { BlazeFaceDetector(context.applicationContext, minScore = 0.65f) }.getOrNull()
    }

    DisposableEffect(detector) {
        onDispose { detector?.close() }
    }

    LaunchedEffect(enabled, detector, viewModel) {
        if (!enabled) {
            faceBox = null
            guidance = "Đã tắt"
            return@LaunchedEffect
        }
        if (detector == null) {
            guidance = "Không tải được AI"
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
                    val detections = try {
                        withContext(Dispatchers.Default) { detector.detect(frame) }
                    } catch (_: Exception) {
                        emptyList()
                    } finally {
                        if (!frame.isRecycled) frame.recycle()
                    }
                    val subject = detections.maxByOrNull {
                        it.box.width().coerceAtLeast(0f) * it.box.height().coerceAtLeast(0f)
                    }
                    if (subject == null) {
                        faceBox = null
                        guidance = "Giữ khuôn mặt trong khung"
                    } else {
                        val box = RectF(subject.box)
                        faceBox = box
                        val centerX = (box.left + box.right) * 0.5f
                        val centerY = (box.top + box.bottom) * 0.5f
                        guidance = when {
                            centerX < 0.38f -> "Chủ thể lệch trái · căn nhẹ sang phải"
                            centerX > 0.62f -> "Chủ thể lệch phải · căn nhẹ sang trái"
                            centerY < 0.30f -> "Chừa thêm khoảng trống phía trên"
                            centerY > 0.72f -> "Đưa máy lên nhẹ để cân khung"
                            else -> "Bố cục ổn · giữ máy và chụp"
                        }
                    }
                }
            }
            delay(850)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (enabled) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val lineColor = Color.White.copy(alpha = 0.42f)
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(w / 3f, 0f), androidx.compose.ui.geometry.Offset(w / 3f, h), 1.dp.toPx())
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(w * 2f / 3f, 0f), androidx.compose.ui.geometry.Offset(w * 2f / 3f, h), 1.dp.toPx())
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, h / 3f), androidx.compose.ui.geometry.Offset(w, h / 3f), 1.dp.toPx())
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, h * 2f / 3f), androidx.compose.ui.geometry.Offset(w, h * 2f / 3f), 1.dp.toPx())
                faceBox?.let { box ->
                    drawRect(
                        color = Color(0xFF67E8A5),
                        topLeft = androidx.compose.ui.geometry.Offset(box.left * w, box.top * h),
                        size = androidx.compose.ui.geometry.Size(box.width() * w, box.height() * h),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            ) {
                Text(
                    text = guidance,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Surface(
            color = if (enabled) Color(0xFF176B4A) else Color.Black.copy(alpha = 0.68f),
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clickable {
                enabled = !enabled
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (enabled) "AI Composition · BẬT" else "AI Composition · TẮT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
