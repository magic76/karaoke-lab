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

/** YouTube-first UI. No fake vocal-removal toggle: capture is diagnostics only. */
class MainActivity : Activity() {
 private lateinit var web:WebView
 private lateinit var status:TextView
 private lateinit var probe:Button
 private var captureRequested=false

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
  footer.addView(status)
  footer.addView(probe)
  root.addView(footer)
  setContentView(root)
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
  if(captureRequested) startService(Intent(this,CaptureService::class.java).setAction(CaptureService.STOP))
  web.destroy()
  super.onDestroy()
 }
}
