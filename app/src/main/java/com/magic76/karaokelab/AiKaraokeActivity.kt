package com.magic76.karaokelab

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * On-device MDX-NET model acquisition and ONNX Runtime validation.
 * IMPORTANT: Audio STFT, model inference over real audio, and iSTFT are not implemented.
 * Never claims a song has been separated.
 */
class AiKaraokeActivity : Activity() {
 private lateinit var status: TextView
 private lateinit var action: Button
 private val modelName = "UVR_MDXNET_3_9662.onnx"
 private val modelUrl = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_3_9662.onnx"
 private val modelBytes = 29704436L
 private val modelSha256 = "e02220e80d8253f4c2209f8924298b2b686bbdf2868b788ff5500fb9bd94aadc"
 private var busy = false
 private fun modelFile() = File(filesDir, modelName)

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val root = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(32,48,32,24)
   setBackgroundColor(Color.rgb(18,22,38))
  }
  root.addView(TextView(this).apply {
   text="手機 AI 模型 · MDX-NET"
   textSize=22f
   setTextColor(Color.WHITE)
  })
  status=TextView(this).apply {
   textSize=15f
   setTextColor(Color.WHITE)
   setPadding(0,24,0,24)
   text="真正的 AI 分離尚在開發中。這個畫面會下載並驗證本機 ONNX 模型，不會上傳歌曲。"
  }
  root.addView(status)
  action=Button(this).apply {
   text="下載／驗證 AI 模型"
   setOnClickListener {confirmDownload()}
  }
  root.addView(action)
  root.addView(Button(this).apply {
   text="返回卡拉 OK"
   setOnClickListener {finish()}
  })
  setContentView(root)
  if(modelFile().exists()) validateAsync()
 }

 private fun confirmDownload() {
  if(busy)return
  AlertDialog.Builder(this)
   .setTitle("下載手機 AI 模型")
   .setMessage("下載約 30 MB 的 MDX-NET ONNX 模型，儲存在手機內。無需帳號、Token 或伺服器。這個版本尚未提供歌曲分離。")
   .setNegativeButton("取消",null)
   .setPositiveButton("下載") {_,_->download()}
   .show()
 }

 private fun download() {
  if(busy)return
  busy=true
  action.isEnabled=false
  status.text="正在下載約 30 MB 模型…"
  Thread {
   val temp=File(filesDir,modelName+".partial")
   var conn:HttpURLConnection?=null
   try {
    conn=URL(modelUrl).openConnection() as HttpURLConnection
    conn.instanceFollowRedirects=true
    conn.connectTimeout=20000
    conn.readTimeout=90000
    conn.setRequestProperty("Accept","application/octet-stream")
    val code=conn.responseCode
    require(code==200) {"下載 HTTP "+code+"（"+conn.url.host+"）"}
    val length=conn.contentLengthLong
    require(length==-1L || length==modelBytes) {"下載大小異常"}
    var count=0L
    conn.inputStream.use {input->
     temp.outputStream().use {output->
      val buffer=ByteArray(65536)
      while(true) {
       val n=input.read(buffer)
       if(n<0)break
       count+=n
       require(count<=modelBytes) {"檔案超出預期大小"}
       output.write(buffer,0,n)
      }
     }
    }
    require(count==modelBytes) {"下載不完整"}
    verify(temp)
    if(modelFile().exists())modelFile().delete()
    require(temp.renameTo(modelFile())) {"無法儲存模型"}
    validateModel()
   }catch(e:Exception) {
    temp.delete()
    runOnUiThread {status.text="模型下載／驗證失敗："+(e.message ?: "未知錯誤")}
   }finally {
    conn?.disconnect()
    runOnUiThread {busy=false;action.isEnabled=true}
   }
  }.start()
 }

 private fun verify(file:File) {
  require(file.length()==modelBytes) {"模型大小錯誤"}
  val md=MessageDigest.getInstance("SHA-256")
  file.inputStream().use {input->
   val buf=ByteArray(65536)
   while(true) {
    val n=input.read(buf)
    if(n<0)break
    md.update(buf,0,n)
   }
  }
  val hash=md.digest().joinToString("") {"%02x".format(it)}
  require(hash==modelSha256) {"模型 SHA-256 驗證失敗"}
 }

 private fun validateAsync() {
  if(busy)return
  busy=true
  action.isEnabled=false
  Thread {
   try {validateModel()}catch(e:Exception) {
    runOnUiThread {status.text="模型驗證失敗："+(e.message ?: "未知錯誤")}
   }finally {
    runOnUiThread {busy=false;action.isEnabled=true}
   }
  }.start()
 }

 private fun validateModel() {
  verify(modelFile())
  val env=OrtEnvironment.getEnvironment()
  val options=OrtSession.SessionOptions()
  try {
   options.setIntraOpNumThreads(2)
   env.createSession(modelFile().absolutePath,options).use {session->
    val input=session.inputInfo.entries.joinToString {it.key}
    val output=session.outputInfo.entries.joinToString {it.key}
    runOnUiThread {
     status.text="模型下載、SHA-256 與 Android ONNX Runtime 載入成功。\n輸入："+input+
      "\n輸出："+output+
      "\n\n尚需實作音訊解碼、STFT、推論、iSTFT 及伴奏播放；現在還不能直接去人聲。"
    }
   }
  }finally {
   options.close()
  }
 }
}
