package com.magic76.karaokelab

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Research-only snapshot: 48 kHz stereo playback PCM -> 44.1 kHz MDX -> instrumental WAV.
 * Does not replace WebView audio or play the WAV over the original soundtrack.
 */
object WebCaptureAiProbe {
 const val SAMPLE_RATE=48000
 const val SECONDS=7
 const val TARGET_FRAMES=SAMPLE_RATE*SECONDS
 fun process(context:Context,interleaved:ShortArray,frames:Int,onProgress:(String)->Unit):String {
  require(frames>0)
  val model=File(context.filesDir,"UVR_MDXNET_3_9662.onnx")
  require(model.isFile) {"尚未下載 ONNX 模型；請先在 AI 模型頁下載"}
  val input=File(context.cacheDir,"webview-ai-input.pcm")
  val output=File(context.cacheDir,"webview-ai-preview.wav")
  val started=System.nanoTime()
  fun ms(since:Long)=(System.nanoTime()-since)/1_000_000
  var resampleMs=0L
  var modelLoadMs=0L
  var inferenceMs=0L
  var inferenceCalls=0
  var pipelineMs=0L
  try {
   // Linear resampling is sufficient for feasibility/latency measurements, not production audio quality.
   val outFrames=(frames.toLong()*44100/SAMPLE_RATE).toInt()
   FileOutputStream(input).use {stream->
    val bytes=ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
    for(i in 0 until outFrames) {
     val pos=i.toDouble()*SAMPLE_RATE/44100
     val a=pos.toInt().coerceIn(0,frames-1)
     val b=(a+1).coerceAtMost(frames-1)
     val t=(pos-a).toFloat()
     for(ch in 0..1) {
      val x=interleaved[a*2+ch]/32768f
      val y=interleaved[b*2+ch]/32768f
      bytes.putFloat(x+(y-x)*t)
     }
     if(bytes.remaining()<8) {stream.write(bytes.array(),0,bytes.position());bytes.clear()}
    }
    if(bytes.position()>0)stream.write(bytes.array(),0,bytes.position())
   }
   resampleMs=ms(started)
   onProgress("AI 推論中")
   val pipelineStart=System.nanoTime()
   PcmFileSource(input).use {source->
    val loadStart=System.nanoTime()
    MdxOnnxModel(model).use {onnx->
     modelLoadMs=ms(loadStart)
     val timed=object:SpectrogramModel {
      override val input:FloatBuffer get()=onnx.input
      override val output:FloatBuffer get()=onnx.output
      override suspend fun run(input:FloatBuffer,output:FloatBuffer) {
       val t=System.nanoTime()
       try {onnx.run(input,output)} finally {inferenceMs+=(System.nanoTime()-t)/1_000_000;inferenceCalls++}
      }
      override fun close() {} // The owning use block closes the underlying model.
     }
     InstrumentalWavSink(source,output).use {sink->
      val params=MdxParams(nFft=6144,dimF=2048,dimT=256,hop=1024,compensate=1.035f,overlap=0.25)
      runBlocking {MdxSeparator(params,timed).demix(source,1f,PrimarySink {pos,l,r,count->
       for(i in 0 until count){l[i]*=params.compensate;r[i]*=params.compensate}
       sink.accept(pos,l,r,count)
      }) {_,_->}}
     }
    }
   }
   pipelineMs=ms(pipelineStart)
   val elapsed=ms(started)
   val audioMs=frames*1000L/SAMPLE_RATE
   val realtime=audioMs.toDouble()/elapsed.coerceAtLeast(1)
   val remaining=pipelineMs-modelLoadMs-inferenceMs
   val verdict=if(realtime>=1.5)"達到速度門檻（仍需驗證串流延遲）" else "未達 1.5× 即時門檻；暫不投入串流整合"
   return "AI 效能：${audioMs}ms 音訊／總計 ${elapsed}ms（${"%.2f".format(java.util.Locale.US,realtime)}×）\\n轉檔 ${resampleMs}ms、模型載入 ${modelLoadMs}ms、ONNX 推論 ${inferenceMs}ms／${inferenceCalls} 次、STFT/iSTFT/寫檔等 ${remaining.coerceAtLeast(0)}ms\\n${verdict}；伴奏 ${output.length()} bytes，未取代原聲"
  } finally {input.delete()}
 }
}
