package com.magic76.karaokelab

import android.app.Activity
import android.os.Bundle
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.view.WindowManager
import android.graphics.Color
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.net.Uri
import org.json.JSONObject

/** Generic WebView karaoke: stereo center cancellation, not AI separation. */
class MainActivity : Activity() {
 private lateinit var web:WebView
 private lateinit var address:EditText
 private lateinit var status:TextView
 private lateinit var toggle:Button
 private lateinit var slider:SeekBar
 private lateinit var percent:TextView
 private lateinit var root:LinearLayout
 private lateinit var navigation:LinearLayout
 private lateinit var footer:LinearLayout
 private lateinit var drawer:LinearLayout
 private lateinit var panel:FrameLayout
 private lateinit var collapse:Button
 private lateinit var handle:Button
 private var controlsOpen=true
 private var enabled=false
 private var retain=15
 private var requestId=0
 private val background=Color.rgb(18,22,38)
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun caption(value:String)=TextView(this).apply { text=value;setTextColor(Color.WHITE);textSize=14f }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.statusBarColor=background
  window.navigationBarColor=background
  window.decorView.systemUiVisibility=0
  window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
  root=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setBackgroundColor(this@MainActivity.background)
   setOnApplyWindowInsetsListener { view,insets ->
    @Suppress("DEPRECATION")
    view.setPadding(0,insets.systemWindowInsetTop,0,insets.systemWindowInsetBottom)
    insets
   }
  }
  navigation=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(4),dp(8),dp(4))}
  address=EditText(this).apply {
   setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)
   setText("https://www.youtube.com/");hint="輸入網站網址"
   inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
   imeOptions=EditorInfo.IME_ACTION_GO
   setOnEditorActionListener { _,_,_ -> navigate();true }
  }
  navigation.addView(address,LinearLayout.LayoutParams(0,dp(48),1f))
  navigation.addView(Button(this).apply {text="前往";isAllCaps=false;setOnClickListener {navigate()}},LinearLayout.LayoutParams(dp(72),dp(48)))
  web=WebView(this).apply {
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   webChromeClient=WebChromeClient()
   webViewClient=object:WebViewClient() {
    override fun onPageFinished(view:WebView?,url:String?) {
     address.setText(url ?: "")
     requestId++
     enabled=false
     toggle.text="開啟卡拉 OK"
     status.text="播放音樂後，可開啟卡拉 OK 模式"
    }
   }
  }
  footer=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(12))}
  toggle=Button(this).apply {text="開啟卡拉 OK";isAllCaps=false;setOnClickListener {setKaraoke(!enabled)}}
  footer.addView(toggle,LinearLayout.LayoutParams(-1,dp(50)))
  val labelRow=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
  labelRow.addView(caption("原聲保留比例"),LinearLayout.LayoutParams(0,-2,1f))
  percent=caption("15%")
  labelRow.addView(percent)
  footer.addView(labelRow)
  slider=SeekBar(this).apply {
   max=100;progress=15
   setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(bar:SeekBar?,value:Int,fromUser:Boolean) {
     retain=value;percent.text="$value%"
     if(enabled && fromUser)setKaraoke(true)
    }
    override fun onStartTrackingTouch(bar:SeekBar?) {}
    override fun onStopTrackingTouch(bar:SeekBar?) {}
   })
  }
  footer.addView(slider)
  status=caption("播放音樂後，可開啟卡拉 OK 模式").apply {textSize=12f}
  footer.addView(status)
  collapse=Button(this).apply {text="收起控制";isAllCaps=false;setOnClickListener {controlsOpen=false;updateControls()}}
  handle=Button(this).apply {text="☰";isAllCaps=false;setOnClickListener {controlsOpen=true;updateControls()}}
  drawer=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(this@MainActivity.background)}
  collapse.text="收起控制"
  handle.text="展開卡拉 OK"
  var swipeStart=0f
  drawer.setOnTouchListener { _,event ->
   when(event.actionMasked) {
    android.view.MotionEvent.ACTION_DOWN -> {swipeStart=event.rawX;true}
    android.view.MotionEvent.ACTION_UP -> {
     if(event.rawX-swipeStart>dp(65) && landscape()) {controlsOpen=false;updateControls()}
     true
    }
    else -> true
   }
  }
  panel=FrameLayout(this)
  setContentView(root)
  layoutForOrientation()
  web.loadUrl("https://www.youtube.com/")
 }
 private fun landscape()=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
 private fun detach(view:View) {(view.parent as? android.view.ViewGroup)?.removeView(view)}
 private fun layoutForOrientation() {
  detach(navigation);detach(web);detach(footer);detach(collapse);detach(handle);detach(drawer);detach(panel)
  root.removeAllViews()
  drawer.removeAllViews()
  panel.removeAllViews()
  if(landscape()) {
   panel.addView(web,FrameLayout.LayoutParams(-1,-1))
   drawer.setPadding(dp(8),dp(8),dp(8),dp(8))
   drawer.addView(collapse,LinearLayout.LayoutParams(-1,dp(44)))
   drawer.addView(navigation,LinearLayout.LayoutParams(-1,dp(56)))
   drawer.addView(footer,LinearLayout.LayoutParams(-1,-2))
   panel.addView(drawer,FrameLayout.LayoutParams(dp(320),-1,Gravity.RIGHT))
   panel.addView(handle,FrameLayout.LayoutParams(dp(56),dp(52),Gravity.RIGHT or Gravity.CENTER_VERTICAL))
   root.addView(panel,LinearLayout.LayoutParams(-1,0,1f))
  } else {
   root.addView(navigation,LinearLayout.LayoutParams(-1,-2))
   root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
   root.addView(collapse,LinearLayout.LayoutParams(-1,dp(42)))
   root.addView(handle,LinearLayout.LayoutParams(-1,dp(42)))
   root.addView(footer,LinearLayout.LayoutParams(-1,-2))
  }
  updateControls()
  root.requestApplyInsets()
 }
 private fun updateControls() {
  if(landscape()) {
   collapse.visibility=View.VISIBLE
   drawer.visibility=if(controlsOpen)View.VISIBLE else View.GONE
   handle.visibility=if(controlsOpen)View.GONE else View.VISIBLE
  } else {
   footer.visibility=if(controlsOpen)View.VISIBLE else View.GONE
   handle.visibility=if(controlsOpen)View.GONE else View.VISIBLE
   collapse.visibility=if(controlsOpen)View.VISIBLE else View.GONE
  }
 }
 override fun onConfigurationChanged(newConfig:Configuration) {
  super.onConfigurationChanged(newConfig)
  layoutForOrientation()
 }
 private fun navigate() {
  val raw=address.text.toString().trim()
  if(raw.isEmpty())return
  val target=if(raw.contains("://"))raw else "https://$raw"
  val uri=Uri.parse(target)
  if((uri.scheme!="https" && uri.scheme!="http") || uri.host.isNullOrBlank()) {
   status.text="請輸入有效的 HTTP/HTTPS 網址";return
  }
  requestId++
  enabled=false;toggle.text="開啟卡拉 OK"
  web.loadUrl(target)
  (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
  address.clearFocus()
 }
 private fun setKaraoke(turnOn:Boolean) {
  val id=++requestId
  val dry=if(turnOn)retain/100.0 else 1.0
  val wet=if(turnOn)1.0-retain/100.0 else 0.0
  val script="""(function(){
   try {
    var media=Array.from(document.querySelectorAll('video,audio'));
    if(!media.length)return JSON.stringify({ok:false,reason:'no-accessible-media'});
    var states=window.__karaokeStates||(window.__karaokeStates=new Map());
    var changed=0,errors=[];
    media.forEach(function(v){
     try {
      var k=states.get(v);
      if(!k) {
       var ctx=new (window.AudioContext||window.webkitAudioContext)();
       var src=ctx.createMediaElementSource(v);
       var split=ctx.createChannelSplitter(2);
       var merge=ctx.createChannelMerger(2);
       var original=ctx.createGain(),processed=ctx.createGain();
       src.connect(original);original.connect(ctx.destination);
       src.connect(split);
       [[0,0,0.5],[1,0,-0.5],[1,1,0.5],[0,1,-0.5]].forEach(function(t){
        var gain=ctx.createGain();gain.gain.value=t[2];
        split.connect(gain,t[0]);gain.connect(merge,0,t[1]);
       });
       merge.connect(processed);processed.connect(ctx.destination);
       k={ctx:ctx,original:original,processed:processed};states.set(v,k);
      }
      k.original.gain.setTargetAtTime(__DRY__,k.ctx.currentTime,0.02);
      k.processed.gain.setTargetAtTime(__WET__,k.ctx.currentTime,0.02);
      k.ctx.resume();changed++;
     }catch(e){errors.push(String(e));}
    });
    return JSON.stringify({ok:changed>0,changed:changed,total:media.length,errors:errors.slice(0,2)});
   }catch(e){return JSON.stringify({ok:false,reason:String(e)});}
  })()""".replace("__DRY__",dry.toString()).replace("__WET__",wet.toString())
  web.evaluateJavascript(script) { raw ->
   if(id!=requestId)return@evaluateJavascript
   try {
    val result=JSONObject(org.json.JSONTokener(raw).nextValue() as String)
    if(result.optBoolean("ok")) {
     enabled=turnOn
     toggle.text=if(enabled)"關閉卡拉 OK" else "開啟卡拉 OK"
     status.text=if(enabled)"卡拉 OK 已啟用 · 效果依音源而異" else "已恢復原始聲音"
    }else status.text="無法處理此頁面音訊："+result.optString("reason",result.optString("errors"))
   }catch(e:Exception){status.text="音訊控制失敗，請重新播放再試"}
  }
 }
 @Deprecated("Deprecated in Java")
 override fun onBackPressed() {
  if(web.canGoBack())web.goBack() else super.onBackPressed()
 }
 override fun onDestroy() {web.destroy();super.onDestroy()}
}
