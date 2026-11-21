package com.magic76.karaokelab
import android.Manifest
import android.app.*
import android.os.Bundle
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.webkit.*
import android.widget.*
class MainActivity: Activity() {
 private lateinit var status:TextView
 private lateinit var web:WebView
 private var karaoke:LocalKaraokePlayer?=null
 private var selected:Uri?=null
 private var mode=0
 private var model:Uri?=null
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  val controls=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
  status=TextView(this).apply { text="Karaoke Lab v0.3 — local karaoke + YouTube capture diagnostics"; setPadding(12,12,12,12) }
  fun button(label:String,action:()->Unit) { controls.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
  button("1. Choose local audio (MP3 / M4A / WAV)") {
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="audio/*"; addCategory(Intent.CATEGORY_OPENABLE) },201)
  }
  val modes=RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
  arrayOf("Original","Center cancel","AI ONNX").forEachIndexed { index,label ->
   modes.addView(RadioButton(this).apply { text=label; id=1000+index })
  }
  modes.check(1000)
  modes.setOnCheckedChangeListener { _,id -> mode=id-1000; karaoke?.mode=mode }
  controls.addView(modes)
  button("Choose AI ONNX model (experimental)") {
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="*/*"; addCategory(Intent.CATEGORY_OPENABLE) },202)
  }
  button("2. Play local karaoke") {
   val uri=selected
   if(uri==null) status.text="Choose a local audio file first"
   else {
    karaoke?.stop()
    karaoke=LocalKaraokePlayer(contentResolver,uri).also {
     it.mode=mode
     it.modelUri=model
     it.onStatus={ msg -> runOnUiThread { status.text=msg } }
     it.play()
    }
   }
  }
  button("Stop local karaoke") { karaoke?.stop(); karaoke=null; status.text="Local playback stopped" }
  button("Start WebView audio capture diagnostic") {
   if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),102)
   else requestCapture()
  }
  button("Stop capture diagnostic") {
   startService(Intent(this,CaptureService::class.java).setAction(CaptureService.STOP))
   status.text="Capture diagnostic stopped"
  }
  val scroll=ScrollView(this).apply { addView(controls) }
  web=WebView(this).apply {
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   webChromeClient=WebChromeClient()
   webViewClient=WebViewClient()
   loadUrl("https://www.youtube.com/")
  }
  root.addView(status)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,0.6f))
  root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
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
  if(requestCode==202 && resultCode==RESULT_OK && data?.data!=null) {
   model=data.data; status.text="Selected ONNX model: "+model.toString()
  } else if(requestCode==201 && resultCode==RESULT_OK && data?.data!=null) {
   selected=data.data
   status.text="Selected: "+selected.toString()
  } else if(requestCode==101 && resultCode==RESULT_OK && data!=null) {
   startForegroundService(Intent(this,CaptureService::class.java).apply {
    action=CaptureService.START; putExtra(CaptureService.CODE,resultCode); putExtra(CaptureService.DATA,data)
   })
   status.text="Capture requested. Check Logcat: KaraokeCapture"
  } else if(requestCode==101) status.text="Capture consent cancelled"
 }
 override fun onDestroy() {
  karaoke?.stop()
  web.destroy()
  super.onDestroy()
 }
}
