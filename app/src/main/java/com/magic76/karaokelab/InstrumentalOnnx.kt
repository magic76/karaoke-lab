package com.magic76.karaokelab

import ai.onnxruntime.*
import java.nio.FloatBuffer

/**
 * Explicit experimental ONNX contract:
 * float32 input [1,2,48000], output [1,2,48000] = instrumental.
 * No arbitrary MDX/Demucs ONNX model can be used without matching preprocessing.
 */
class InstrumentalOnnx(modelBytes:ByteArray):AutoCloseable {
 private val environment=OrtEnvironment.getEnvironment()
 private val session=environment.createSession(modelBytes,OrtSession.SessionOptions())
 val inputName:String
 init {
  require(session.inputNames.size==1) { "Model must have exactly one input" }
  require(session.outputNames.size==1) { "Model must have exactly one output" }
  inputName=session.inputNames.first()
  val input=session.inputInfo[inputName]?.info as? TensorInfo ?: error("Input must be tensor")
  require(input.type==OnnxJavaType.FLOAT && input.shape.contentEquals(longArrayOf(1,2,48000))) {
   "Model must accept float32 [1,2,48000]"
  }
  val output=session.outputInfo.values.first().info as? TensorInfo ?: error("Output must be tensor")
  require(output.type==OnnxJavaType.FLOAT && output.shape.contentEquals(longArrayOf(1,2,48000))) {
   "Model must output float32 [1,2,48000]"
  }
 }
 @Synchronized fun instrumental(interleaved:ShortArray):ShortArray {
  require(interleaved.size==96000)
  val planar=FloatArray(96000)
  for(i in 0 until 48000) {
   planar[i]=interleaved[i*2]/32768f
   planar[i+48000]=interleaved[i*2+1]/32768f
  }
  OnnxTensor.createTensor(environment,FloatBuffer.wrap(planar),longArrayOf(1,2,48000)).use { tensor ->
   session.run(mapOf(inputName to tensor)).use { result ->
    val output=result[0] as? OnnxTensor ?: error("Invalid output")
    val data=output.floatBuffer
    val samples=ShortArray(96000)
    for(i in 0 until 48000) {
     val l=data.get(i).coerceIn(-1f,1f)
     val r=data.get(i+48000).coerceIn(-1f,1f)
     samples[i*2]=(l*32767f).toInt().toShort()
     samples[i*2+1]=(r*32767f).toInt().toShort()
    }
    return samples
   }
  }
 }
 override fun close() { session.close() }
}
