package com.magic76.karaokelab

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.widget.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Local model download and inference-engine validation; no server or upload. */
class AiKaraokeActivity:Activity() {
 private lateinit var status:TextView
 private lateinit var action:Button
 private val modelName="UVR_MDXNET_9482.fp16acc.tflite"
 private val modelUrl="https://huggingface.co/gyoom-sa/UVR-MDX-LiteRT/resolve/main/"+modelName
 private fun modelFile()=File(filesDir,modelName)
 private var busy=false
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL;setPadding(32,48,32,24)
   setBackgroundColor(Color.rgb(18,22,38))
  }
  root.addView(TextView(this).apply {text="手機 AI 模型";textSize=24f;setTextColor(Color.WHITE)})
  status=TextView(this).apply {
   textSize=15f;setTextColor(Color.WHITE);setPadding(0,24,0,24)
   text="本機模型測試：尚未接通音訊分離或 YouTube。"
  }
  root.addView(status)
  action=Button(this).apply {text="下載／驗證本機 AI 模型";setOnClickListener {confirmDownload()}}
  root.addView(action)
  root.addView(Button(this).apply {text="匯入已下載模型";setOnClickListener {pickModel()}})
  root.addView(Button(this).apply {text="返回卡拉 OK";setOnClickListener {finish()}})
  setContentView(root)
  if(modelFile().exists())validateAsync()
 }

 private fun pickModel() {
  if(busy)return
  startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
   addCategory(Intent.CATEGORY_OPENABLE)
   type="*/*"
  },4203)
 }
 @Deprecated("Deprecated in Java")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==4203 && resultCode==RESULT_OK) data?.data?.let {importModel(it)}
 }
 private fun importModel(uri:Uri) {
  if(busy)return
  busy=true;action.isEnabled=false;status.text="正在匯入模型…"
  Thread {
   val temp=File(filesDir,modelName+".import")
   try {
    var count=0L
    contentResolver.openInputStream(uri)?.use { input ->
     temp.outputStream().use { output ->
      val buffer=ByteArray(65536)
      while(true) {
       val n=input.read(buffer)
       if(n<0)break
       count+=n
       require(count<=45000000L) {"檔案過大"}
       output.write(buffer,0,n)
      }
     }
    } ?: error("無法讀取檔案")
    require(count>=20000000L) {"模型檔案不完整"}
    val digest=java.security.MessageDigest.getInstance("SHA-256")
    temp.inputStream().use { input ->
     val buffer=ByteArray(65536)
     while(true) {
      val n=input.read(buffer)
      if(n<0)break
      digest.update(buffer,0,n)
     }
    }
    val sha=digest.digest().joinToString("") {"%02x".format(it)}
    require(sha=="2a07e11db13a11ca4900a54b4a316ef67931e993a6a3d19444bccbbeb9b445ee") {"SHA-256 不符"}
    if(modelFile().exists())modelFile().delete()
    require(temp.renameTo(modelFile())) {"無法儲存模型"}
    validateModel()
   }catch(e:Exception) {
    temp.delete()
    runOnUiThread {status.text="匯入失敗："+(e.message ?: "未知錯誤")}
   }finally {
    runOnUiThread {busy=false;action.isEnabled=true}
   }
  }.start()
 }
 private fun confirmDownload() {
  if(busy)return
  AlertDialog.Builder(this).setTitle("下載 AI 模型")
   .setMessage("下載約 28 MB 的模型到手機。無須伺服器、Token，也不會上傳音樂。目前僅測試模型載入，尚未完成音訊分離。")
   .setNegativeButton("取消",null)
   .setPositiveButton("下載") {_,_->download()}
   .show()
 }
 private fun download() {
  if(busy)return
  busy=true;action.isEnabled=false;status.text="正在下載模型…"
  Thread {
   val partial=File(filesDir,modelName+".partial")
   var conn:HttpURLConnection?=null
   try {
    conn=URL(modelUrl+"?download=true").openConnection() as HttpURLConnection
    conn.setRequestProperty("User-Agent","KaraokeLab/0.20 Android")
    conn.setRequestProperty("Accept","application/octet-stream")
    conn.connectTimeout=20000;conn.readTimeout=60000
    require(conn.responseCode==200) {"模型下載 HTTP "+conn.responseCode+"（"+conn.url.host+"），來源拒絕存取；可稍後重試或換網路，不需 API Key"}
    val length=conn.contentLengthLong
    require(length<0 || length in 20000000L..45000000L) {"模型大小異常"}
    var count=0L
    conn.inputStream.use { input ->
     partial.outputStream().use { output ->
      val buffer=ByteArray(65536)
      while(true) {
       val n=input.read(buffer)
       if(n<0)break
       count+=n
       require(count<=45000000L) {"模型過大"}
       output.write(buffer,0,n)
      }
     }
    }
    require(count>=20000000L) {"下載不完整"}
    val digest=java.security.MessageDigest.getInstance("SHA-256")
    partial.inputStream().use { input ->
     val buf=ByteArray(65536)
     while(true) {val n=input.read(buf);if(n<0)break;digest.update(buf,0,n)}
    }
    val sha=digest.digest().joinToString("") {"%02x".format(it)}
    require(sha=="2a07e11db13a11ca4900a54b4a316ef67931e993a6a3d19444bccbbeb9b445ee") {"模型 SHA-256 不符"}
    if(modelFile().exists())modelFile().delete()
    require(partial.renameTo(modelFile())) {"儲存失敗"}
    validateModel()
   }catch(e:Exception) {
    partial.delete()
    runOnUiThread {status.text="下載／載入失敗："+(e.message ?: "未知錯誤")}
   }finally {
    conn?.disconnect()
    runOnUiThread {busy=false;action.isEnabled=true}
   }
  }.start()
 }
 private fun validateAsync() {
  if(busy)return
  busy=true;action.isEnabled=false
  Thread {
   try {validateModel()}catch(e:Exception) {
    runOnUiThread {status.text="模型載入失敗："+(e.message ?: "未知錯誤")}
   }finally {runOnUiThread {busy=false;action.isEnabled=true}}
  }.start()
 }
 private fun validateModel() {
  val file=modelFile()
  require(file.length()>=20000000L) {"模型不完整"}
  val mapped=java.io.RandomAccessFile(file,"r").use {
   it.channel.map(FileChannel.MapMode.READ_ONLY,0,file.length())
  }
  mapped.order(ByteOrder.nativeOrder())
  Interpreter(mapped,Interpreter.Options().setNumThreads(2)).use { engine ->
   val input=engine.getInputTensor(0).shape().joinToString(" × ")
   val output=engine.getOutputTensor(0).shape().joinToString(" × ")
   runOnUiThread {
    status.text="已在手機載入真正的 AI 模型。\n輸入："+input+
     "\n輸出："+output+
     "\n\n尚未實作 STFT、分段推論、音訊重建；現在不能去人聲。"
   }
  }
 }
}
