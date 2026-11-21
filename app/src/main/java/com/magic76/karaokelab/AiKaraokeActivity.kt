package com.magic76.karaokelab

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.media.MediaPlayer
import kotlinx.coroutines.runBlocking
import android.graphics.Color
import android.widget.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * On-device MDX-NET model acquisition and ONNX Runtime validation.
 * Experimental local-file MDX-NET pipeline. Not validated on physical devices yet.
 */
class AiKaraokeActivity : Activity() {
 private lateinit var status: TextView
 private lateinit var action: Button
 private val modelName = "UVR_MDXNET_3_9662.onnx"
 private val modelUrl = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_3_9662.onnx"
 private val modelBytes = 29704436L
 private val modelSha256 = "e02220e80d8253f4c2209f8924298b2b686bbdf2868b788ff5500fb9bd94aadc"
 private var busy = false
 private var player:MediaPlayer?=null
 private lateinit var processButton:Button
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
   text=if(intent.getBooleanExtra("webview_only",false))"下載／驗證 WebView AI 實驗使用的 ONNX 模型。此頁不需選擇本機歌曲。" else "實驗版：可選擇本機 44.1 kHz 歌曲、下載模型並產生伴奏。尚未經手機實測；YouTube 影片不能直接使用這條流程。"
  }
  root.addView(status)
  action=Button(this).apply {
   text="下載／驗證 AI 模型"
   setOnClickListener {confirmDownload()}
  }
  root.addView(action)
  if(!intent.getBooleanExtra("webview_only",false))root.addView(Button(this).apply {text="選擇本機歌曲";setOnClickListener {pickAudio()}})
  processButton=Button(this).apply {text="AI 去人聲（本機檔案）";setOnClickListener {separateLocal()}}
  if(!intent.getBooleanExtra("webview_only",false))root.addView(processButton)
  root.addView(Button(this).apply {
   text="返回卡拉 OK"
   setOnClickListener {finish()}
  })
  setContentView(root)
  if(modelFile().exists()) validateAsync()
 }


 private fun pickAudio() {
  if(busy)return
  startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
   addCategory(Intent.CATEGORY_OPENABLE)
   type="*/*"
   putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("audio/*","video/*"))
  },4204)
 }
 @Deprecated("Deprecated in Java")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==4204 && resultCode==RESULT_OK)data?.data?.let {decodeAudio(it)}
 }
 private fun decodeAudio(uri:Uri) {
  if(busy)return
  busy=true;action.isEnabled=false;status.text="解碼中…"
  Thread {
   try {
    val pcm=File(cacheDir,"ai-source.pcm")
    pcm.delete()
    val result=LocalAudioDecoder.decode(this,uri,pcm)
    runOnUiThread {status.text="本機音訊解碼完成："+result.frames+" frames，44.1 kHz 雙聲道。可按 AI 去人聲開始處理。"}
   }catch(e:Exception) {
    runOnUiThread {status.text="解碼失敗："+(e.message ?: "未知錯誤")}
   }finally {runOnUiThread {busy=false;action.isEnabled=true}}
  }.start()
 }
 private fun separateLocal() {
  if(busy)return
  if(!modelFile().exists()){status.text="請先下載 AI 模型";return}
  val pcm=File(cacheDir,"ai-source.pcm")
  if(!pcm.exists() || pcm.length()==0L){status.text="請先選擇本機歌曲並完成解碼";return}
  busy=true;action.isEnabled=false;processButton.isEnabled=false
  Thread {
   val output=File(cacheDir,"ai-instrumental.wav")
   try {
    verify(modelFile())
    PcmFileSource(pcm).use {src->
     MdxOnnxModel(modelFile()).use {model->
      InstrumentalWavSink(src,output).use {sink->
       val params=MdxParams(nFft=6144,dimF=2048,dimT=256,hop=1024,compensate=1.035f,overlap=0.25)
       runBlocking {
        MdxSeparator(params,model).demix(src,1f,PrimarySink {pos,l,r,count->
         for(i in 0 until count){l[i]*=params.compensate;r[i]*=params.compensate}
         sink.accept(pos,l,r,count)
        }) {done,total->
         if(done==total || done%8==0)runOnUiThread {status.text="AI 分離中："+(done*100/total)+"%"}
        }
       }
      }
     }
    }
    runOnUiThread {
     status.text="伴奏已產生，準備播放："+output.length()+" bytes"
     player?.release()
     player=MediaPlayer().apply {
      setDataSource(output.absolutePath)
      prepare()
      start()
     }
    }
   }catch(e:Exception) {
    output.delete()
    runOnUiThread {status.text="AI 分離失敗："+(e.message ?: "未知錯誤")}
   }finally {runOnUiThread {busy=false;action.isEnabled=true;processButton.isEnabled=true}}
  }.start()
 }
 override fun onDestroy(){player?.release();player=null;super.onDestroy()}
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
