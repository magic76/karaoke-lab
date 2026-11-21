package com.magic76.karaokelab
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.*
import android.os.*
import android.util.Log
import kotlin.math.*
class CaptureService:Service() {
 companion object { const val START="capture.start"; const val STOP="capture.stop"; const val CODE="code"; const val DATA="data"; const val SELF_TEST="capture.selftest"; const val AI_PROBE="capture.ai_probe" }
 private var projection:MediaProjection?=null
 private var recorder:AudioRecord?=null
 private var testPlayer:AudioTrack?=null
 private var testThread:Thread?=null
 @Volatile private var testRunning=false
 private var thread:Thread?=null
 @Volatile private var running=false
 @Volatile private var aiRequested=false
 @Volatile private var aiBusy=false
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate() {
  super.onCreate()
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture","Capture probe",NotificationManager.IMPORTANCE_LOW))
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action==STOP) { stopSelf(); return START_NOT_STICKY }
  if(intent?.action==AI_PROBE) {
   if(running && !aiBusy) {
    aiRequested=true
    getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("ai_status","等待擷取 7 秒 WebView 音訊…").apply()
   } else getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("ai_status","請先啟動擷取，或等待上一次處理完成").apply()
   return START_NOT_STICKY
  }
  if(intent?.action==SELF_TEST) { if(running)startSelfTest() else getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("error","擷取尚未啟動，無法測試").apply(); return START_NOT_STICKY }
  if(intent?.action!=START || running) return START_NOT_STICKY
  val note=Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Karaoke Lab").setContentText("Analyzing capture level; no audio saved").build()
  startForeground(1,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  try {
   @Suppress("DEPRECATION")
   val data=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(DATA,Intent::class.java) else intent.getParcelableExtra(DATA)
   val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   projection=manager.getMediaProjection(intent.getIntExtra(CODE,Activity.RESULT_CANCELED),requireNotNull(data))
   projection?.registerCallback(object:MediaProjection.Callback() { override fun onStop() { stopSelf() } },Handler(mainLooper))
   val format=AudioFormat.Builder().setSampleRate(48000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
   val config=AudioPlaybackCaptureConfiguration.Builder(requireNotNull(projection)).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build()
   val minimum=AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT)
   recorder=AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum,19200)).setAudioPlaybackCaptureConfig(config).build()
   check(recorder?.state==AudioRecord.STATE_INITIALIZED)
   recorder?.startRecording(); running=true
   getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("error","").apply()
   thread=Thread {
    val samples=ShortArray(4800)
    var aiSamples:ShortArray?=null
    var aiOffset=0
    while(running) {
     val count=recorder?.read(samples,0,samples.size)?:break
     if(count>0) {
      if(aiRequested && aiSamples==null && !aiBusy) {
       aiRequested=false;aiBusy=true
       aiSamples=ShortArray(WebCaptureAiProbe.TARGET_FRAMES*2)
       aiOffset=0
      }
      val dest=aiSamples
      if(dest!=null) {
       val copied=minOf(count,dest.size-aiOffset)
       System.arraycopy(samples,0,dest,aiOffset,copied)
       aiOffset+=copied
       if(aiOffset>=dest.size) {
        aiSamples=null
        getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("ai_status","已取得 7 秒 PCM，AI 處理中…").apply()
        Thread {
         try {
          val result=WebCaptureAiProbe.process(this@CaptureService,dest,aiOffset/2) {message->
           getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("ai_status",message).apply()
          }
          getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("ai_status",result).apply()
         }catch(e:Exception) {
          getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("ai_status","AI 處理失敗："+(e.message ?: e.javaClass.simpleName)).apply()
         }finally {aiBusy=false}
        }.start()
       }
      }
      var sum=0.0; var peak=0
      for(i in 0 until count) { val v=samples[i].toInt(); sum+=v.toDouble()*v; peak=max(peak,abs(v)) }
      val rms=sqrt(sum/count)/32768.0
      val peakValue=peak/32768.0
      val prefs=getSharedPreferences("capture_probe",MODE_PRIVATE)
      val testAge=System.currentTimeMillis()-prefs.getLong("selftest_started",0)
      if(testAge in 0..3500){val prior=java.lang.Double.longBitsToDouble(prefs.getLong("selftest_peak",0));if(peakValue>prior)prefs.edit().putLong("selftest_peak",java.lang.Double.doubleToRawLongBits(peakValue)).apply()}
      prefs.edit()
       .putLong("timestamp",System.currentTimeMillis())
       .putLong("rms",java.lang.Double.doubleToRawLongBits(rms))
       .putLong("peak",java.lang.Double.doubleToRawLongBits(peakValue))
       .putLong("frames_read",count.toLong())
       .putString("recorder_state","recording").apply()
      Log.i("KaraokeCapture","samples="+count+" rms="+rms+" peak="+peakValue)
     } else Log.w("KaraokeCapture","read="+count)

    }
   }.apply { start() }
  } catch(e:Exception) { Log.e("KaraokeCapture","Capture failed",e); getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("error",e.message ?: e.javaClass.simpleName).apply(); stopSelf() }
  return START_NOT_STICKY
 }
 private fun startSelfTest() {
  if(testRunning)return
  val rate=48000
  val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL).build()
  val format=AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
  val bufferSize=maxOf(AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT),rate/5*2)
  try {
   val track=AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format).setBufferSizeInBytes(bufferSize).setTransferMode(AudioTrack.MODE_STREAM).build()
   testPlayer=track
   testRunning=true
   getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putLong("selftest_started",System.currentTimeMillis()).apply()
   testThread=Thread {
    try {
     val data=ShortArray(960)
     track.play()
     var frame=0
     while(testRunning && frame<rate*3) {
      for(i in data.indices) {
       val fade=minOf(1.0,(rate*3-frame-i).coerceAtLeast(0).toDouble()/(rate/20))
       data[i]=(kotlin.math.sin(2.0*Math.PI*440.0*(frame+i)/rate)*6000.0*fade).toInt().toShort()
      }
      val written=track.write(data,0,data.size)
      if(written<=0)break
      frame+=written
     }
    } catch(e:Exception) {
     getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("error","測試音播放失敗："+(e.message ?: "unknown")).apply()
    } finally {
     testRunning=false
     try {track.stop()} catch(_:Exception){}
     track.release()
     if(testPlayer===track)testPlayer=null
    }
   }.apply {start()}
  }catch(e:Exception){testRunning=false;getSharedPreferences("capture_probe",MODE_PRIVATE).edit().putString("error","無法建立測試音："+(e.message ?: "unknown")).apply()}
 }
 override fun onDestroy() {
  running=false
  testRunning=false
  try {testPlayer?.pause();testPlayer?.flush()} catch(_:Exception) {}
  try { recorder?.stop() } catch(_:Exception) {}
  thread?.interrupt(); recorder?.release(); recorder=null
  projection?.stop(); projection=null
  super.onDestroy()
 }
}