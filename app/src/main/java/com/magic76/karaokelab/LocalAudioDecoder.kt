package com.magic76.karaokelab

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes a user-selected local media file to interleaved stereo float32 PCM.
 * Initial vertical slice: 44.1kHz input only; explicit failure for other rates.
 * Does not separate vocals. Caller runs this on a worker thread.
 */
object LocalAudioDecoder {
 data class Result(val pcm:File,val frames:Long,val sampleRate:Int)
 fun decode(context:Context,uri:Uri,destination:File):Result {
  val extractor=MediaExtractor()
  var codec:MediaCodec?=null
  try {
   extractor.setDataSource(context,uri,null)
   val track=(0 until extractor.trackCount).firstOrNull {
    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
   } ?: error("找不到音軌")
   val format=extractor.getTrackFormat(track)
   val mime=format.getString(MediaFormat.KEY_MIME) ?: error("音軌格式未知")
   val rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
   require(rate==44100) {"目前只支援 44.1 kHz，來源是 $rate Hz"}
   extractor.selectTrack(track)
   codec=MediaCodec.createDecoderByType(mime)
   codec.configure(format,null,null,0)
   codec.start()
   var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
   require(channels in 1..2) {"目前只支援單聲道或雙聲道"}
   var frames=0L
   var inputDone=false
   var outputDone=false
   val info=MediaCodec.BufferInfo()
   FileOutputStream(destination).use {stream->
    while(!outputDone) {
     if(!inputDone) {
      val index=codec.dequeueInputBuffer(10000)
      if(index>=0) {
       val buffer=codec.getInputBuffer(index) ?: error("輸入緩衝區不存在")
       val size=extractor.readSampleData(buffer,0)
       if(size<0) {
        codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        inputDone=true
       }else {
        codec.queueInputBuffer(index,0,size,extractor.sampleTime,0)
        extractor.advance()
       }
      }
     }
     when(val index=codec.dequeueOutputBuffer(info,10000)) {
      MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
       val out=codec.outputFormat
       channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
       require(channels in 1..2) {"解碼後聲道不支援"}
       require(out.getInteger(MediaFormat.KEY_SAMPLE_RATE)==44100) {"解碼後取樣率不支援"}
      }
      MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
      else -> if(index>=0) {
       val buffer=codec.getOutputBuffer(index) ?: error("輸出緩衝區不存在")
       buffer.position(info.offset)
       buffer.limit(info.offset+info.size)
       val formatCode=codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
       val bytes=ByteArray(info.size)
       buffer.get(bytes)
       val pcm=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
       val sampleCount=when(formatCode) {
        android.media.AudioFormat.ENCODING_PCM_16BIT -> bytes.size/2
        android.media.AudioFormat.ENCODING_PCM_FLOAT -> bytes.size/4
        else -> error("不支援的 PCM 編碼：$formatCode")
       }
       require(sampleCount%channels==0) {"PCM frame 不完整"}
       val out=ByteBuffer.allocate(sampleCount/channels*2*4).order(ByteOrder.LITTLE_ENDIAN)
       repeat(sampleCount/channels) {
        val left=if(formatCode==android.media.AudioFormat.ENCODING_PCM_FLOAT)pcm.float else pcm.short/32768f
        val right=if(channels==2) {
         if(formatCode==android.media.AudioFormat.ENCODING_PCM_FLOAT)pcm.float else pcm.short/32768f
        }else left
        out.putFloat(left);out.putFloat(right)
       }
       stream.write(out.array())
       frames+=sampleCount/channels
       codec.releaseOutputBuffer(index,false)
       if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)outputDone=true
      }
     }
    }
   }
   return Result(destination,frames,rate)
  }catch(e:Exception) {
   destination.delete()
   throw e
  }finally {
   try {codec?.stop()}catch(_:Exception){}
   codec?.release()
   extractor.release()
  }
 }
}
