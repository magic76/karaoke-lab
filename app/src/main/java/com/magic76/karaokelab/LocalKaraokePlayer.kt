package com.magic76.karaokelab

import android.content.ContentResolver
import android.media.*
import android.net.Uri
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Local-media DSP research player. Center cancellation is NOT AI source separation.
 * Processes owned/local media only. Never accesses WebView/YouTube streams.
 */
class LocalKaraokePlayer(private val resolver:ContentResolver,private val uri:Uri) {
 @Volatile var mode:Int=0
 @Volatile var modelUri:Uri?=null
 @Volatile var onStatus:((String)->Unit)?=null
 private val active=AtomicBoolean(false)
 private var thread:Thread?=null
 @Volatile private var track:AudioTrack?=null
 fun play() {
  if(!active.compareAndSet(false,true)) return
  thread=Thread { decode() }.apply { name="LocalKaraokeDecoder"; start() }
 }
 fun stop() {
  active.set(false)
  try { track?.pause(); track?.flush() } catch(_:Exception) {}
  thread?.interrupt()
 }
 private fun decode() {
  val extractor=MediaExtractor()
  var codec:MediaCodec?=null
  var inference:InstrumentalOnnx?=null
  val aiPending=java.io.ByteArrayOutputStream()
  try {
   resolver.openAssetFileDescriptor(uri,"r")?.use { afd ->
    extractor.setDataSource(afd.fileDescriptor,afd.startOffset,afd.length)
   } ?: error("Cannot open audio")
   var audioIndex=-1
   for(i in 0 until extractor.trackCount) {
    if(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true) { audioIndex=i; break }
   }
   check(audioIndex>=0) { "No audio track" }
   extractor.selectTrack(audioIndex)
   val format=extractor.getTrackFormat(audioIndex)
   val mime=requireNotNull(format.getString(MediaFormat.KEY_MIME))
   codec=MediaCodec.createDecoderByType(mime)
   codec.configure(format,null,null,0)
   codec.start()
   if(mode==2) {
    val uri=modelUri ?: error("Choose an ONNX model before AI playback")
    val bytes=resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open ONNX model")
    require(bytes.size<=200_000_000) { "Model exceeds 200 MB limit" }
    inference=InstrumentalOnnx(bytes)
   }
   val info=MediaCodec.BufferInfo()
   var inputDone=false
   var outputDone=false
   var sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
   var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
   while(active.get()&&!outputDone) {
    if(!inputDone) {
     val inputIndex=codec.dequeueInputBuffer(10000)
     if(inputIndex>=0) {
      val input=requireNotNull(codec.getInputBuffer(inputIndex))
      val n=extractor.readSampleData(input,0)
      if(n<0) {
       codec.queueInputBuffer(inputIndex,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
       inputDone=true
      } else {
       codec.queueInputBuffer(inputIndex,0,n,extractor.sampleTime,0)
       extractor.advance()
      }
     }
    }
    val index=codec.dequeueOutputBuffer(info,10000)
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
     val output=codec.outputFormat
     sampleRate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
     channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
     val encoding=if(output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
     check(encoding==AudioFormat.ENCODING_PCM_16BIT) { "Unsupported decoded PCM encoding: $encoding" }
     check(channels==1||channels==2) { "Only mono/stereo supported" }
     val mask=if(channels==2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
     val min=AudioTrack.getMinBufferSize(sampleRate,mask,AudioFormat.ENCODING_PCM_16BIT)
     check(min>0)
     track=AudioTrack.Builder()
      .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
      .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
      .setBufferSizeInBytes(max(min,sampleRate*channels*2/4))
      .setTransferMode(AudioTrack.MODE_STREAM).build()
     if(inference!=null) check(sampleRate==48000 && channels==2) { "AI mode requires stereo 48 kHz audio" }
     track?.play()
     onStatus?.invoke("Playing local audio: $sampleRate Hz, $channels ch. Center cancel is experimental DSP, not AI.")
    } else if(index>=0) {
     val output=codec.getOutputBuffer(index)
     if(info.size>0 && output!=null) {
      val bytes=ByteArray(info.size)
      output.position(info.offset); output.limit(info.offset+info.size)
      output.get(bytes)
      if(mode==1 && channels==2) {
       val pcm=java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
       for(i in 0 until bytes.size-3 step 4) {
        val left=pcm.getShort(i).toInt()
        val right=pcm.getShort(i+2).toInt()
        val difference=((left-right)/2).toShort()
        pcm.putShort(i,difference)
        pcm.putShort(i+2,(-difference.toInt()).toShort())
       }
      }
      if(inference!=null) {
       aiPending.write(bytes)
       val blockSize=48000*2*2
       while(active.get() && aiPending.size()>=blockSize) {
        val pending=aiPending.toByteArray()
        val pcm=java.nio.ByteBuffer.wrap(pending,0,blockSize).order(ByteOrder.LITTLE_ENDIAN)
        val input=ShortArray(96000) { pcm.short }
        val started=System.nanoTime()
        val output=requireNotNull(inference).instrumental(input)
        val elapsedMs=(System.nanoTime()-started)/1_000_000
        onStatus?.invoke("AI block: "+elapsedMs+"ms / 1000ms audio")
        val outBytes=java.nio.ByteBuffer.allocate(blockSize).order(ByteOrder.LITTLE_ENDIAN)
        output.forEach { outBytes.putShort(it) }
        var offset=0
        while(active.get() && offset<blockSize) {
         val count=track?.write(outBytes.array(),offset,blockSize-offset,AudioTrack.WRITE_BLOCKING) ?: -1
         check(count>0) { "AudioTrack write failed: $count" }
         offset+=count
        }
        aiPending.reset()
        aiPending.write(pending,blockSize,pending.size-blockSize)
       }
       codec.releaseOutputBuffer(index,false)
       if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0) outputDone=true
       continue
      }
      var written=0
      while(active.get() && written<bytes.size) {
       val n=track?.write(bytes,written,bytes.size-written,AudioTrack.WRITE_BLOCKING) ?: -1
       if(n<=0) error("AudioTrack write failed: $n")
       written+=n
      }
     }
     codec.releaseOutputBuffer(index,false)
     if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0) outputDone=true
    }
   }
   if(outputDone && inference!=null && aiPending.size()>0) onStatus?.invoke("AI tail omitted: less than 1 second")
   if(outputDone) onStatus?.invoke("Playback complete")
  } catch(e:Exception) {
   if(active.get()) onStatus?.invoke("Playback error: "+e.message)
  } finally {
   active.set(false)
   try { track?.stop() } catch(_:Exception) {}
   track?.release(); track=null
   try { codec?.stop() } catch(_:Exception) {}
   codec?.release()
   inference?.close()
   extractor.release()
  }
 }
}
