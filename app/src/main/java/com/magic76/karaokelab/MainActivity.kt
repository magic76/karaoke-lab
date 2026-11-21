package com.magic76.karaokelab
import android.Manifest
import android.app.*
import android.os.Bundle
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.webkit.*
import android.widget.*
class MainActivity: Activity() {
 private lateinit var status: TextView
 private lateinit var web: WebView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  status=TextView(this).apply { text="Research capture probe (no vocal removal)"; setPadding(16,16,16,16) }
  val start=Button(this).apply { text="Start capture test" }
  val stop=Button(this).apply { text="Stop capture" }
  web=WebView(this).apply { settings.javaScriptEnabled=true; settings.domStorageEnabled=true; webChromeClient=WebChromeClient(); webViewClient=WebViewClient(); loadUrl("https://www.youtube.com/") }
  root.addView(status); root.addView(start); root.addView(stop)
  root.addView(web,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
  start.setOnClickListener {
   if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),102)
   else requestCapture()
  }
  stop.setOnClickListener { startService(Intent(this,CaptureService::class.java).setAction(CaptureService.STOP)); status.text="Stopped" }
 }
 private fun requestCapture() {
  val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(manager.createScreenCaptureIntent(),101)
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==102 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) requestCapture()
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==101 && resultCode==RESULT_OK && data!=null) {
   startForegroundService(Intent(this,CaptureService::class.java).apply {
    action=CaptureService.START; putExtra(CaptureService.CODE,resultCode); putExtra(CaptureService.DATA,data)
   })
   status.text="Capture requested; see Logcat KaraokeCapture"
  } else status.text="Consent cancelled"
 }
 override fun onDestroy() { web.destroy(); super.onDestroy() }
}