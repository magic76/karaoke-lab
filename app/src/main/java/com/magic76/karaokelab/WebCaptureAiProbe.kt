package com.magic76.karaokelab

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
   onProgress("AI 推論中")
   PcmFileSource(input).use {source->
    MdxOnnxModel(model).use {onnx->
     InstrumentalWavSink(source,output).use {sink->
      val params=MdxParams(nFft=6144,dimF=2048,dimT=256,hop=1024,compensate=1.035f,overlap=0.25)
      runBlocking {MdxSeparator(params,onnx).demix(source,1f,PrimarySink {pos,l,r,count->
       for(i in 0 until count){l[i]*=params.compensate;r[i]*=params.compensate}
       sink.accept(pos,l,r,count)
      }) {_,_->}}
     }
    }
   }
   val elapsed=(System.nanoTime()-started)/1_000_000
   return "WebView PCM AI 處理完成：${frames/SAMPLE_RATE.toDouble()} 秒音訊，耗時 ${elapsed} ms，伴奏 ${output.length()} bytes（尚未替換 YouTube 原聲）"
  } finally {input.delete()}
 }
}
