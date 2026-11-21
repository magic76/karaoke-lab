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
 companion object { const val START="capture.start"; const val STOP="capture.stop"; const val CODE="code"; const val DATA="data" }
 private var projection:MediaProjection?=null
 private var recorder:AudioRecord?=null
 private var thread:Thread?=null
 @Volatile private var running=false
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate() {
  super.onCreate()
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture","Capture probe",NotificationManager.IMPORTANCE_LOW))
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action==STOP) { stopSelf(); return START_NOT_STICKY }
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
   thread=Thread {
    val samples=ShortArray(4800)
    while(running) {
     val count=recorder?.read(samples,0,samples.size)?:break
     if(count>0) {
      var sum=0.0; var peak=0
      for(i in 0 until count) { val v=samples[i].toInt(); sum+=v.toDouble()*v; peak=max(peak,abs(v)) }
      val rms=sqrt(sum/count)/32768.0
      val peakValue=peak/32768.0
      getSharedPreferences("capture_probe",MODE_PRIVATE).edit()
       .putLong("timestamp",System.currentTimeMillis())
       .putLong("rms",java.lang.Double.doubleToRawLongBits(rms))
       .putLong("peak",java.lang.Double.doubleToRawLongBits(peakValue)).apply()
      Log.i("KaraokeCapture","samples="+count+" rms="+rms+" peak="+peakValue)
     } else Log.w("KaraokeCapture","read="+count)

    }
   }.apply { start() }
  } catch(e:Exception) { Log.e("KaraokeCapture","Capture failed",e); stopSelf() }
  return START_NOT_STICKY
 }
 override fun onDestroy() {
  running=false
  try { recorder?.stop() } catch(_:Exception) {}
  thread?.interrupt(); recorder?.release(); recorder=null
  projection?.stop(); projection=null
  super.onDestroy()
 }
}