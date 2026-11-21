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
import android.os.Handler
import android.os.Looper

/** Experimental YouTube in-page stereo center cancellation; may fail for cross-origin media. */
class MainActivity : Activity() {
 private lateinit var web:WebView
 private lateinit var status:TextView
 private lateinit var probe:Button
 private var captureRequested=false
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
  root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  val footer=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(16,8,16,12)
  }
  status=TextView(this).apply {
   text="播放 YouTube MV 後，按「實驗：YouTube 去人聲」。這是立體聲中置消除，不是 AI；部分影片可能無聲。"
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
  pcmText=TextView(this).apply { text="PCM: 無即時資料" }
  val karaoke=Button(this).apply {
   text="實驗：YouTube 去人聲"
   setOnClickListener {
    // Audio source routing is attempted inside the WebView's page context.
    // Cross-origin media may yield silence. This is not AI separation.
    val script="""(function(){
     try {
      var v=document.querySelector('video');
      if(!v) return JSON.stringify({ok:false,reason:'no-video-in-top-frame'});
      if(v.muted) v.muted=false;
      var k=window.__karaokeProbe;
      if(k && k.video!==v) {try{k.context.close()}catch(e){};k=null;window.__karaokeProbe=null;}
      if(!k) {
       var ctx=new (window.AudioContext||window.webkitAudioContext)();
       var source=ctx.createMediaElementSource(v);
       var split=ctx.createChannelSplitter(2);
       var merge=ctx.createChannelMerger(2);
       var dry=ctx.createGain(),wet=ctx.createGain();
       source.connect(dry);dry.connect(ctx.destination);
       source.connect(split);
       [[0,0,0.5],[1,0,-0.5],[1,1,0.5],[0,1,-0.5]].forEach(function(t){
        var g=ctx.createGain();g.gain.value=t[2];split.connect(g,t[0]);g.connect(merge,0,t[1]);
       });
       merge.connect(wet);wet.connect(ctx.destination);
       k={video:v,context:ctx,dry:dry,wet:wet,enabled:false};
       window.__karaokeProbe=k;
      }
      k.enabled=!k.enabled;
      k.dry.gain.value=k.enabled?0:1;
      k.wet.gain.value=k.enabled?1:0;
      k.context.resume();
      return JSON.stringify({ok:true,enabled:k.enabled,context:k.context.state,channels:2,method:'stereo-center-cancel',warning:'cross-origin-media-may-output-silence'});
     } catch(e) {return JSON.stringify({ok:false,reason:String(e)});}
    })()"""
    web.evaluateJavascript(script) { result ->
     status.text="去人聲實驗結果："+result+"。若完全無聲，可能是跨來源音訊限制；重新整理頁面可恢復。"
    }
   }
  }
  footer.addView(status)
  footer.addView(pcmText)
  footer.addView(karaoke)
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
 override fun onDestroy() {
  uiHandler.removeCallbacks(meterTask)
  if(captureRequested) startService(Intent(this,CaptureService::class.java).setAction(CaptureService.STOP))
  web.destroy()
  super.onDestroy()
 }
}
