package com.hinnka.mycamera.ml

import org.tensorflow.lite.Interpreter

/**
 * EDSR x4 LiteRT model. The 7.71 MB model is downloaded on demand and verified by SHA-256.
 * Apache-2.0 model card: litert-community/EDSR-x4-LiteRT.
 */
object AiSuperResolutionModelManager : HfModelInstaller(
    tag = "AiSuperResolutionModelManager",
    directory = "ml_models/edsr_x4",
    fileName = "edsr.tflite",
    expectedSizeBytes = 7_714_836L,
    sha256 = "77fdb8655436844e94187ce6675f5c8c73ed5bf3080327d8ebe96e15da829bde",
    path = "/litert-community/EDSR-x4-LiteRT/resolve/553600d/edsr.tflite",
    legacyDirectories = emptyList(),
    checkContract = ::checkEdsrContract,
)

private fun checkEdsrContract(interpreter: Interpreter) {
        check(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1) {
            "EDSR x4 must have one input and one output"
        }
        check(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 3, 128, 128))) {
            "Unexpected EDSR input tensor shape"
        }
        check(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 3, 512, 512))) {
            "Unexpected EDSR output tensor shape"
        }
        check(interpreter.getInputTensor(0).dataType() == org.tensorflow.lite.DataType.FLOAT32)
        check(interpreter.getOutputTensor(0).dataType() == org.tensorflow.lite.DataType.FLOAT32)
}
