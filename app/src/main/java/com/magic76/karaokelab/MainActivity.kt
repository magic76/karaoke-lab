package com.magic76.karaokelab

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import android.net.Uri
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/** YouTube-first UI. No fake vocal-removal toggle: capture is diagnostics only. */
class MainActivity : Activity() {
 private lateinit var web:WebView
 private lateinit var status:TextView
 private lateinit var probe:Button
 private var captureRequested=false
 private var videoUri:Uri?=null
 private var localPlayer:LocalKaraokePlayer?=null
 private lateinit var video:VideoView
 private lateinit var localButton:Button
 private var localMode=false
 private var youtubeMuted=false
 private lateinit var pcmText:TextView
 private val uiHandler=Handler(Looper.getMainLooper())
 private val meterTask=object:Runnable { override fun run() {
  val p=getSharedPreferences("capture_probe",MODE_PRIVATE)
  val age=System.currentTimeMillis()-p.getLong("timestamp",0)
  pcmText.text=if(captureRequested && age in 0..2000) "PCM RMS: %.5f / Peak: %.5f".format(java.util.Locale.US,java.lang.Double.longBitsToDouble(p.getLong("rms",0)),java.lang.Double.longBitsToDouble(p.getLong("peak",0))) else "PCM: 無即時資料"
  uiHandler.postDelayed(this,500)
 } }

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  web=WebView(this).apply {
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   webChromeClient=WebChromeClient()
   webViewClient=WebViewClient()
   loadUrl("https://www.youtube.com/")
  }
  video=VideoView(this).apply {
   setOnPreparedListener { player ->
    player.setVolume(0f,0f)
    status.text="影片原聲已靜音；伴奏由獨立播放器輸出（同步仍待驗證）"
   }
  }
  root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(video,LinearLayout.LayoutParams(-1,0,1f))
  video.visibility=android.view.View.GONE
  val footer=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(16,8,16,12)
  }
  status=TextView(this).apply {
   text="播放 YouTube MV。音訊加工尚在驗證；目前不會去除原唱。"
   textSize=13f
  }
  probe=Button(this).apply {
   text="測試音訊擷取"
   setOnClickListener {
    if(captureRequested) {
     startService(Intent(this@MainActivity,CaptureService::class.java).setAction(CaptureService.STOP))
     captureRequested=false
     text="測試音訊擷取"
     status.text="已停止擷取測試"
    } else if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
     requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),102)
    } else requestCapture()
   }
  }
  localButton=Button(this).apply {
   text="測試可控影片音訊替換"
   setOnClickListener {
    if(localMode) {
     stopLocal()
     web.visibility=android.view.View.VISIBLE
     video.visibility=android.view.View.GONE
     localMode=false
     text="測試可控影片音訊替換"
     status.text="已返回 YouTube（YouTube 音訊未加工）"
    } else {
     startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      type="video/*"; addCategory(Intent.CATEGORY_OPENABLE)
     },301)
    }
   }
  }
  pcmText=TextView(this).apply { text="PCM: 無即時資料" }
  val mute=Button(this).apply {
   text="測試 YouTube 靜音"
   setOnClickListener {
    val desired=!youtubeMuted
    val script="(function(){var a=Array.from(document.querySelectorAll('video,audio'));a.forEach(function(v){v.muted="+desired+";});return JSON.stringify({found:a.length,muted:a.filter(function(v){return v.muted;}).length});})()"
    web.evaluateJavascript(script) { result ->
     status.text="WebView 媒體控制結果: "+result+"。請實際確認聲音是否靜音，再觀察 PCM。"
     youtubeMuted=desired
     text=if(youtubeMuted) "恢復 YouTube 原聲" else "測試 YouTube 靜音"
    }
   }
  }
  footer.addView(status)
  footer.addView(pcmText)
  footer.addView(mute)
  footer.addView(localButton)
  footer.addView(probe)
  root.addView(footer)
  setContentView(root)
  uiHandler.post(meterTask)
 }
 private fun requestCapture() {
  val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(manager.createScreenCaptureIntent(),101)
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==102 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) requestCapture()
  else if(requestCode==102) status.text="需要音訊擷取權限才能測試"
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==301 && resultCode==RESULT_OK && data?.data!=null) {
   videoUri=data.data
   val uri=videoUri ?: return
   stopLocal()
   localMode=true
   web.onPause()
   web.visibility=android.view.View.GONE
   video.visibility=android.view.View.VISIBLE
   video.setVideoURI(uri)
   video.setOnPreparedListener { player ->
    player.setVolume(0f,0f)
    localPlayer=LocalKaraokePlayer(contentResolver,uri).apply {
     mode=1
     onStatus={ msg -> runOnUiThread { status.text=msg } }
    }
    video.start()
    localPlayer?.play()
    status.text="本機影片已靜音，獨立輸出中置消除音訊；請檢查影音同步"
   }
   video.setOnCompletionListener { stopLocal() }
   localButton.text="返回 YouTube"
   return
  }
  if(requestCode!=101) return
  if(resultCode==RESULT_OK && data!=null) {
   startForegroundService(Intent(this,CaptureService::class.java).apply {
    action=CaptureService.START
    putExtra(CaptureService.CODE,resultCode)
    putExtra(CaptureService.DATA,data)
   })
   captureRequested=true
   probe.text="停止音訊擷取測試"
   status.text="擷取測試中（只記錄訊號強度，不會改變 YouTube 聲音）"
  } else status.text="已取消音訊擷取授權"
 }
 private fun stopLocal() { localPlayer?.stop(); localPlayer=null; video.stopPlayback(); web.onResume() }
 override fun onDestroy() {
  uiHandler.removeCallbacks(meterTask)
  stopLocal()
  if(captureRequested) startService(Intent(this,CaptureService::class.java).setAction(CaptureService.STOP))
  web.destroy()
  super.onDestroy()
 }
}
