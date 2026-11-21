package com.magic76.karaokelab

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** MDX-NET 3_9662 ONNX inference; model produces a vocals stem. */
class MdxOnnxModel(modelFile: File, threads:Int=2, provider:String="CPU") : SpectrogramModel {
 private val env=OrtEnvironment.getEnvironment()
 private val options=OrtSession.SessionOptions().apply {
  setIntraOpNumThreads(threads.coerceIn(1,8))
  when(provider) {
   "CPU" -> Unit
   "XNNPACK" -> addXnnpack(mapOf("intra_op_num_threads" to threads.coerceIn(1,8).toString()))
   "NNAPI" -> addNnapi()
   else -> error("Unknown provider: $provider")
  }
 }
 private val session=try {env.createSession(modelFile.absolutePath,options)}catch(t:Throwable){options.close();throw t}
 private val count=4*2048*256
 override val input:FloatBuffer=ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
 override val output:FloatBuffer=ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
 override suspend fun run(input:FloatBuffer,output:FloatBuffer) {
  input.rewind()
  OnnxTensor.createTensor(env,input,longArrayOf(1,4,2048,256)).use {tensor->
   session.run(mapOf("input" to tensor)).use {results->
    val result=results[0] as OnnxTensor
    val floats=result.floatBuffer
    floats.rewind()
    require(floats.remaining()==count) {"模型輸出形狀不符合 MDX-NET 3_9662"}
    output.rewind()
    output.put(floats)
    output.rewind()
   }
  }
 }
 override fun close(){session.close();options.close()}
}
