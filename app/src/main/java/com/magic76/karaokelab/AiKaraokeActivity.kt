package com.magic76.karaokelab

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.widget.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Local-file AI separation via a user-configured HTTPS Demucs server. */
class AiKaraokeActivity:Activity() {
 private lateinit var message:TextView
 private lateinit var video:VideoView
 private lateinit var choose:Button
 private var busy=false

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(24,36,24,24)
   setBackgroundColor(Color.rgb(18,22,38))
  }
  message=TextView(this).apply {
   setTextColor(Color.WHITE);textSize=15f
   text="AI 人聲分離：選擇有權處理的本機歌曲或影片。需要自行設定 HTTPS Demucs 伺服器。"
  }
  root.addView(message)
  choose=Button(this).apply {text="選擇歌曲／影片";setOnClickListener {pickFile()}}
  root.addView(choose)
  root.addView(Button(this).apply {text="設定 AI 伺服器";setOnClickListener {configure()}})
  video=VideoView(this)
  root.addView(video,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(Button(this).apply {text="返回網頁播放器";setOnClickListener {finish()}})
  setContentView(root)
 }
 private fun configure() {
  val prefs=getSharedPreferences("ai",MODE_PRIVATE)
  val fields=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(24,8,24,0)}
  val server=EditText(this).apply {
   hint="https://your-server/separate";setSingleLine(true)
   setText(prefs.getString("url",""))
  }
  val token=EditText(this).apply {
   hint="Bearer Token";setSingleLine(true)
   inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
   setText(prefs.getString("token",""))
  }
  fields.addView(server);fields.addView(token)
  AlertDialog.Builder(this).setTitle("AI 伺服器")
   .setMessage("本機檔案會上傳至你指定的伺服器，請確認隱私與授權。")
   .setView(fields).setNegativeButton("取消",null)
   .setPositiveButton("儲存") {_,_->
    val url=server.text.toString().trim()
    val secret=token.text.toString().trim()
    if(!url.startsWith("https://") || secret.isEmpty()) {
     message.text="請設定 HTTPS API 網址及 Token"
    }else {
     prefs.edit().putString("url",url).putString("token",secret).apply()
     message.text="伺服器已設定，可以選擇檔案"
    }
   }.show()
 }
 private fun pickFile() {
  val prefs=getSharedPreferences("ai",MODE_PRIVATE)
  if(prefs.getString("url","").isNullOrEmpty() || prefs.getString("token","").isNullOrEmpty()) {
   configure();return
  }
  if(busy)return
  val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
   addCategory(Intent.CATEGORY_OPENABLE);type="*/*"
   putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("audio/*","video/*"))
  }
  startActivityForResult(intent,4102)
 }
 @Deprecated("Deprecated in Java")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==4102 && resultCode==RESULT_OK) data?.data?.let {process(it)}
 }
 private fun process(uri:Uri) {
  if(busy)return
  busy=true;choose.isEnabled=false
  message.text="正在上傳並執行 AI 分離，可能需要數分鐘…"
  val prefs=getSharedPreferences("ai",MODE_PRIVATE)
  val endpoint=prefs.getString("url","") ?: ""
  val token=prefs.getString("token","") ?: ""
  Thread {
   var conn:HttpURLConnection?=null
   try {
    val target=URL(endpoint)
    require(target.protocol=="https")
    val boundary="KaraokeAI"+System.currentTimeMillis()
    conn=(target.openConnection() as HttpURLConnection).apply {
     requestMethod="POST";doOutput=true
     connectTimeout=20000;readTimeout=3600000
     setRequestProperty("Authorization","Bearer "+token)
     setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary)
     setChunkedStreamingMode(1024*1024)
    }
    conn.outputStream.use { output ->
     output.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"input\"\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray())
     contentResolver.openInputStream(uri)?.use {it.copyTo(output)}
      ?: error("無法讀取檔案")
     output.write(("\r\n--"+boundary+"--\r\n").toByteArray())
    }
    if(conn.responseCode!=200) error("伺服器回應 HTTP "+conn.responseCode)
    val isVideo=conn.contentType?.contains("video/")==true
    val file=File(cacheDir,if(isVideo)"ai-result.mp4" else "ai-result.wav")
    conn.inputStream.use {input->file.outputStream().use {input.copyTo(it)}}
    runOnUiThread {
     video.stopPlayback()
     video.setVideoPath(file.absolutePath)
     video.setOnPreparedListener {it.isLooping=false;video.start()}
     message.text="AI 分離完成，播放純伴奏"
    }
   }catch(e:Exception) {
    runOnUiThread {message.text="AI 分離失敗："+(e.message ?: e.javaClass.simpleName)}
   }finally {
    conn?.disconnect()
    runOnUiThread {busy=false;choose.isEnabled=true}
   }
  }.start()
 }
 override fun onDestroy() {video.stopPlayback();super.onDestroy()}
}
