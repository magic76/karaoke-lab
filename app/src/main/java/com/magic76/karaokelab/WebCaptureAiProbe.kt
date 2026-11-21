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
   val variants=mutableListOf<String>()
   var fastestMs=Long.MAX_VALUE
   var fastestThreads=2
   // Run both CPU thread settings on the SAME captured PCM in one user action.
   // Keep only the first WAV; the second run is a no-output performance comparison.
   for(threads in listOf(2,4)) {
    onProgress("CPU ${threads} 執行緒測試中（共兩組）…")
    val pipelineStart=System.nanoTime()
    var localInferenceMs=0L
    var localCalls=0
    var localLoadMs=0L
    PcmFileSource(input).use {source->
     val loadStart=System.nanoTime()
     MdxOnnxModel(model,threads).use {onnx->
      localLoadMs=ms(loadStart)
      val timed=object:SpectrogramModel {
       override val input:FloatBuffer get()=onnx.input
       override val output:FloatBuffer get()=onnx.output
       override suspend fun run(input:FloatBuffer,output:FloatBuffer) {
        val t=System.nanoTime()
        try {onnx.run(input,output)} finally {
         localInferenceMs+=(System.nanoTime()-t)/1_000_000
         localCalls++
        }
       }
       override fun close() {}
      }
      val params=MdxParams(nFft=6144,dimF=2048,dimT=256,hop=1024,compensate=1.035f,overlap=0.25)
      if(threads==2) {
       InstrumentalWavSink(source,output).use {sink->
        runBlocking {MdxSeparator(params,timed).demix(source,1f,PrimarySink {pos,l,r,count->
         for(i in 0 until count){l[i]*=params.compensate;r[i]*=params.compensate}
         sink.accept(pos,l,r,count)
        }) {_,_->}}
       }
      } else {
       runBlocking {MdxSeparator(params,timed).demix(source,1f,PrimarySink {_,_,_,_->}) {_,_->}}
      }
     }
    }
    val total=ms(pipelineStart)
    if(total<fastestMs){fastestMs=total;fastestThreads=threads}
    variants.add("${threads} 執行緒：總計 ${total}ms、載入 ${localLoadMs}ms、推論 ${localInferenceMs}ms／${localCalls} 次、其餘 ${(total-localLoadMs-localInferenceMs).coerceAtLeast(0)}ms")
    if(threads==2) {
     modelLoadMs=localLoadMs
     inferenceMs=localInferenceMs
     inferenceCalls=localCalls
     pipelineMs=total
    }
   }
   val elapsed=ms(started)
   val audioMs=frames*1000L/SAMPLE_RATE
   val realtime=audioMs.toDouble()/fastestMs.coerceAtLeast(1)
   val verdict=if(realtime>=1.5)"達到速度門檻（仍需驗證串流延遲）" else "未達 1.5× 即時門檻；暫不投入串流整合"
   return "AI 兩組效能測試（${audioMs}ms 音訊）：\n"+variants.joinToString("\n")+"\n轉檔 ${resampleMs}ms、兩組測試合計 ${elapsed}ms；最快 ${fastestThreads} 執行緒、速度 ${"%.2f".format(java.util.Locale.US,realtime)}×。${verdict}。伴奏 ${output.length()} bytes，未取代原聲"
  } finally {input.delete()}
 }
}
