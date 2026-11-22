package com.hinnka.mycamera.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.ai.edge.litert.CompiledModel
import com.hinnka.mycamera.utils.PLog
import java.io.File

/**
 * Local EDSR x4 super-resolution.
 *
 * The model accepts fixed 128x128 NCHW RGB tiles and returns 512x512 NCHW RGB.
 * Overlapping tiles are stitched at overlap midpoints to avoid double-writing seams.
 * The output is capped at 32 MP to protect mid-range phone memory.
 *
 * GPU is required for this model; silently falling back to its very slow CPU path would
 * make the camera/editor appear frozen on mobile devices.
 */
object AiSuperResolution {
    private const val TAG = "AiSuperResolution"
    private const val INPUT = 128
    private const val OUTPUT = 512
    private const val CHANNELS = 3
    private const val INPUT_ELEMENTS = INPUT * INPUT * CHANNELS
    private const val OUTPUT_ELEMENTS = OUTPUT * OUTPUT * CHANNELS
    private const val OVERLAP = 16
    private const val MAX_OUTPUT_PIXELS = 32_000_000L

    fun upscale4x(
        context: Context,
        input: Bitmap,
        onProgress: (Float) -> Unit,
    ): Bitmap {
        val outputWidth = input.width.toLong() * 4L
        val outputHeight = input.height.toLong() * 4L
        require(input.width > 0 && input.height > 0)
        require(outputWidth * outputHeight <= MAX_OUTPUT_PIXELS) {
            "Ảnh quá lớn để phóng đại 4x an toàn trên điện thoại. Hãy dùng ảnh nhỏ hơn."
        }

        val modelFile = AiSuperResolutionModelManager.installedModelFileOrNull(context)
            ?: throw IllegalStateException("Chưa tải mô hình AI Super Resolution 4x.")
        val cache = MlDelegateCacheFactory.create(
            context = context,
            tag = TAG,
            cacheName = "edsr_x4_fp16",
            modelAssetName = modelFile.name,
            modelSizeBytes = modelFile.length().toInt(),
            modelFingerprint = AiSuperResolutionModelManager.sha256,
        )

        val started = SystemClock.elapsedRealtime()
        val gpu = LiteRtGpuModel.fromFile(
            file = modelFile,
            cache = cache,
            precision = CompiledModel.GpuOptions.Precision.FP16,
            inputElementCount = INPUT_ELEMENTS,
            outputElementCount = OUTPUT_ELEMENTS,
        )

        try {
            val xs = tileOrigins(input.width)
            val ys = tileOrigins(input.height)
            val total = xs.size * ys.size
            val output = Bitmap.createBitmap(
                outputWidth.toInt(),
                outputHeight.toInt(),
                Bitmap.Config.ARGB_8888,
                true,
                input.colorSpace
            )
            val inputPixels = IntArray(INPUT * INPUT)
            val inputTensor = FloatArray(INPUT_ELEMENTS)
            var completed = 0

            try {
                gpu.use { backend ->
                    for (row in ys.indices) {
                        val tileY = ys[row]
                        val top = if (row == 0) 0 else midpoint(ys[row - 1] + INPUT, tileY)
                        val bottom = if (row == ys.lastIndex) input.height
                            else midpoint(tileY + INPUT, ys[row + 1])
                        for (col in xs.indices) {
                            val tileX = xs[col]
                            val left = if (col == 0) 0 else midpoint(xs[col - 1] + INPUT, tileX)
                            val right = if (col == xs.lastIndex) input.width
                                else midpoint(tileX + INPUT, xs[col + 1])

                            readInputTile(input, tileX, tileY, inputPixels, inputTensor)
                            val sr = backend.run(inputTensor)
                            writeOutputRegion(sr, output, tileX, tileY, left, top, right, bottom)
                            completed++
                            onProgress(completed.toFloat() / total)
                        }
                    }
                }
                PLog.d(TAG, "EDSR x4 completed: ${input.width}x${input.height} -> ${output.width}x${output.height}, tiles=${total}, ms=${SystemClock.elapsedRealtime() - started}")
                return output
            } catch (error: Throwable) {
                output.recycle()
                throw error
            }
        } catch (error: OutOfMemoryError) {
            PLog.e(TAG, "EDSR x4 ran out of memory", error)
            throw IllegalStateException("Không đủ RAM để xử lý ảnh 4x. Ảnh gốc vẫn được giữ nguyên.", error)
        } finally {
            gpu.close()
        }
    }

    private fun tileOrigins(length: Int): IntArray {
        if (length <= INPUT) return intArrayOf(0)
        val result = ArrayList<Int>()
        var position = 0
        while (true) {
            result += position
            if (position + INPUT >= length) break
            position = minOf(position + INPUT - OVERLAP, length - INPUT)
        }
        return result.toIntArray()
    }

    private fun midpoint(a: Int, b: Int): Int = (a + b) / 2

    private fun readInputTile(
        source: Bitmap,
        tileX: Int,
        tileY: Int,
        pixels: IntArray,
        tensor: FloatArray,
    ) {
        for (y in 0 until INPUT) {
            val sy = (tileY + y).coerceAtMost(source.height - 1)
            for (x in 0 until INPUT) {
                val sx = (tileX + x).coerceAtMost(source.width - 1)
                pixels[y * INPUT + x] = source.getPixel(sx, sy)
            }
        }
        val plane = INPUT * INPUT
        for (i in pixels.indices) {
            val pixel = pixels[i]
            tensor[i] = ((pixel shr 16) and 0xff) / 255f
            tensor[plane + i] = ((pixel shr 8) and 0xff) / 255f
            tensor[plane * 2 + i] = (pixel and 0xff) / 255f
        }
    }

    private fun writeOutputRegion(
        tensor: FloatArray,
        destination: Bitmap,
        tileX: Int,
        tileY: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val regionWidth = (right - left) * 4
        val regionHeight = (bottom - top) * 4
        val rowPixels = IntArray(regionWidth)
        val plane = OUTPUT * OUTPUT
        for (y in top until bottom) {
            val localY = (y - tileY) * 4
            for (dy in 0 until 4) {
                val outputY = localY + dy
                for (x in left until right) {
                    val localX = (x - tileX) * 4
                    for (dx in 0 until 4) {
                        val outputX = localX + dx
                        val index = outputY * OUTPUT + outputX
                        val red = (tensor[index].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                        val green = (tensor[plane + index].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                        val blue = (tensor[plane * 2 + index].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                        rowPixels[(x - left) * 4 + dx] =
                            (0xff shl 24) or (red shl 16) or (green shl 8) or blue
                    }
                }
                destination.setPixels(
                    rowPixels, 0, regionWidth,
                    left * 4, (y * 4) + dy,
                    regionWidth, 1
                )
            }
        }
    }
}
