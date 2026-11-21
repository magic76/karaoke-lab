package com.magic76.karaokelab

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** Interleaved stereo little-endian float32 PCM file source. */
class PcmFileSource(file:File):StereoSource,AutoCloseable {
 private val raf=RandomAccessFile(file,"r")
 override val frames:Long=raf.length()/8
 override fun read(pos:Long,l:FloatArray,r:FloatArray,off:Int,count:Int) {
  require(pos>=0 && pos+count<=frames)
  val bytes=ByteArray(count*8)
  raf.seek(pos*8)
  raf.readFully(bytes)
  val bb=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
  for(i in 0 until count){l[off+i]=bb.float;r[off+i]=bb.float}
 }
 override fun close(){raf.close()}
}

/** Writes 44.1kHz 16-bit stereo WAV; subtracts predicted vocals from original mix. */
class InstrumentalWavSink(private val source:StereoSource,output:File):PrimarySink,AutoCloseable {
 private val file=RandomAccessFile(output,"rw")
 private var written=0L
 init {file.setLength(0);file.write(ByteArray(44))}
 override fun accept(pos:Long,primaryL:FloatArray,primaryR:FloatArray,count:Int) {
  require(pos==written) {"非連續輸出，預期 $written，收到 $pos"}
  val l=FloatArray(count)
  val r=FloatArray(count)
  source.read(pos,l,r,0,count)
  val bb=ByteBuffer.allocate(count*4).order(ByteOrder.LITTLE_ENDIAN)
  for(i in 0 until count) {
   val left=((l[i]-primaryL[i]).coerceIn(-1f,1f)*32767f).toInt()
   val right=((r[i]-primaryR[i]).coerceIn(-1f,1f)*32767f).toInt()
   bb.putShort(left.toShort());bb.putShort(right.toShort())
  }
  file.write(bb.array())
  written+=count
 }
 override fun close() {
  try {
   val bytes=written*4
   require(bytes<=0xffffffffL-36) {"WAV 檔案超過 4GB"}
   file.seek(0)
   val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
   h.put("RIFF".toByteArray(Charsets.US_ASCII))
   h.putInt((36+bytes).toInt())
   h.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
   h.putInt(16);h.putShort(1);h.putShort(2)
   h.putInt(44100);h.putInt(44100*4);h.putShort(4);h.putShort(16)
   h.put("data".toByteArray(Charsets.US_ASCII));h.putInt(bytes.toInt())
   file.write(h.array())
  }finally {file.close()}
 }
}
