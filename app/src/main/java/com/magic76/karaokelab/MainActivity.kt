package com.magic76.karaokelab

import android.app.Activity
import android.os.Bundle
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.view.WindowManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
 private lateinit var handle:Button
 private lateinit var quickRow:LinearLayout
 private lateinit var quickToggle:Button
 private lateinit var quickFullscreen:Button
 private lateinit var fullscreen:Button
 private lateinit var bassSlider:SeekBar
 private lateinit var volumeSlider:SeekBar
 private lateinit var bassPercent:TextView
 private lateinit var volumePercent:TextView
 private var immersive=false
 private var customVideo:View?=null
 private var customVideoCallback:WebChromeClient.CustomViewCallback?=null
 private var floatYFraction=0.5f
 private var floatXFraction=1f
 private var controlsOpen=true
 private var enabled=false
 private var retain=0
 private var bass=75
 private var volume=100
 private var requestId=0
 private val background=Color.rgb(18,22,38)
 private val accent=Color.rgb(41,211,208)
 private fun shape(color:Int,radius:Int=14,stroke:Int=0):GradientDrawable =
  GradientDrawable().apply {
   setColor(color);cornerRadius=dp(radius).toFloat()
   if(stroke!=0)setStroke(dp(1),stroke)
  }
 private fun styledButton(label:String,primary:Boolean=false)=Button(this).apply {
  text=label;isAllCaps=false;textSize=14f
  typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
  setTextColor(if(primary)this@MainActivity.background else Color.WHITE)
  backgroundTintList=null
  background=shape(if(primary)accent else Color.rgb(37,43,65),14,
   if(primary)0 else Color.rgb(70,78,105))
  minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0
  setPadding(dp(12),0,dp(12),0)
 }
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
  address.background=shape(Color.rgb(33,39,61),12,Color.rgb(65,74,99))
  address.setPadding(dp(12),0,dp(12),0)
  navigation.addView(address,LinearLayout.LayoutParams(0,dp(46),1f))
  navigation.addView(styledButton("前往").apply {setOnClickListener {navigate()}},
   LinearLayout.LayoutParams(dp(68),dp(46)).apply {leftMargin=dp(6)})
  web=WebView(this).apply {
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   webChromeClient=object:WebChromeClient() {
    override fun onShowCustomView(view:View?,callback:CustomViewCallback?) {
     if(view==null || customVideo!=null) {callback?.onCustomViewHidden();return}
     customVideo=view
     customVideoCallback=callback
     detach(view)
     panel.addView(view,FrameLayout.LayoutParams(-1,-1))
     view.bringToFront()
     handle.bringToFront()
     applySystemBars()
    }
    override fun onHideCustomView() {closeCustomVideo()}
   }
   webViewClient=object:WebViewClient() {
    override fun onPageFinished(view:WebView?,url:String?) {
     address.setText(url ?: "")
     requestId++
     enabled=false
     toggle.text="開啟卡拉 OK"
     quickToggle.text="開啟卡拉 OK"
     status.text="播放音樂後，可開啟卡拉 OK 模式"
    }
   }
  }
  footer=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL
   setPadding(dp(14),dp(10),dp(14),dp(12))
   background=shape(Color.rgb(25,30,48),18)
  }
  toggle=styledButton("開啟卡拉 OK",true).apply {setOnClickListener {setKaraoke(!enabled)}}
  val mainRow=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
  footer.addView(mainRow)
  mainRow.addView(toggle,LinearLayout.LayoutParams(0,dp(44),1f))
  fullscreen=styledButton("⛶  全螢幕").apply {setOnClickListener { immersive=!immersive;if(immersive)controlsOpen=false;applySystemBars();layoutForOrientation() }}
  mainRow.addView(fullscreen,LinearLayout.LayoutParams(0,dp(44),1f).apply {leftMargin=dp(8)})
  fun addControl(label:String,initial:Int,onValue:(Int)->Unit):Pair<SeekBar,TextView> {
   val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;setPadding(dp(4),dp(7),dp(4),0)}
   row.addView(caption(label),LinearLayout.LayoutParams(0,-2,1f))
   val value=caption("$initial%")
   row.addView(value)
   footer.addView(row)
   val bar=SeekBar(this).apply {
    max=100;progress=initial
    setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
     override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) {
      value.text="$progress%"
      if(fromUser)onValue(progress)
     }
     override fun onStartTrackingTouch(bar:SeekBar?) {}
     override fun onStopTrackingTouch(bar:SeekBar?) {}
    })
   }
   footer.addView(bar,LinearLayout.LayoutParams(-1,dp(36)))
   return bar to value
  }
  val vocalControl=addControl("人聲消除強度",100) {value->
   retain=100-value
   if(enabled)setKaraoke(true)
  }
  slider=vocalControl.first
  percent=vocalControl.second
  val bassControl=addControl("低音保留",bass) {value->bass=value;if(enabled)setKaraoke(true)}
  bassSlider=bassControl.first
  bassPercent=bassControl.second
  val volumeControl=addControl("輸出音量",volume) {value->volume=value;if(enabled)setKaraoke(true)}
  volumeSlider=volumeControl.first
  volumePercent=volumeControl.second
  footer.addView(styledButton("收起控制").apply {setOnClickListener {controlsOpen=false;updateControls()}},
   LinearLayout.LayoutParams(-1,dp(38)).apply {topMargin=dp(4)})
  status=caption("播放音樂後，可開啟卡拉 OK 模式").apply {textSize=12f}
  footer.addView(status)
  handle=styledButton("♫  卡拉 OK",true)
  quickRow=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;setPadding(dp(3),dp(3),dp(3),dp(3));background=shape(Color.rgb(25,30,48),14)}
  quickToggle=styledButton("開啟卡拉 OK",true).apply {textSize=12f;setOnClickListener {setKaraoke(!enabled)}}
  quickFullscreen=styledButton("⛶ 全螢幕").apply {textSize=12f;setOnClickListener {immersive=!immersive;if(immersive)controlsOpen=false;applySystemBars();layoutForOrientation()}}
  quickRow.addView(quickToggle,LinearLayout.LayoutParams(0,dp(40),1f))
  quickRow.addView(quickFullscreen,LinearLayout.LayoutParams(0,dp(40),1f).apply {leftMargin=dp(5)})
  drawer=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(this@MainActivity.background)}

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
  handle.setOnTouchListener(object:View.OnTouchListener {
   private var downX=0f
   private var downY=0f
   private var startX=0f
   private var startY=0f
   private var dragging=false
   override fun onTouch(v:View,event:MotionEvent):Boolean {
    if(!landscape()) {
     if(event.actionMasked==MotionEvent.ACTION_UP) {controlsOpen=!controlsOpen;updateControls()}
     return true
    }
    when(event.actionMasked) {
     MotionEvent.ACTION_DOWN -> {
      downX=event.rawX;downY=event.rawY
      startX=v.x;startY=v.y
      dragging=false;return true
     }
     MotionEvent.ACTION_MOVE -> {
      val dx=event.rawX-downX;val dy=event.rawY-downY
      if(kotlin.math.abs(dx)>dp(8) || kotlin.math.abs(dy)>dp(8))dragging=true
      if(dragging) {
       val maxX=(panel.width-v.width).coerceAtLeast(0).toFloat()
       val maxY=(panel.height-v.height).coerceAtLeast(0).toFloat()
       v.x=(startX+dx).coerceIn(0f,maxX)
       v.y=(startY+dy).coerceIn(0f,maxY)
      }
      return true
     }
     MotionEvent.ACTION_UP -> {
      if(dragging) {
       val maxX=(panel.width-v.width).coerceAtLeast(1).toFloat()
       val maxY=(panel.height-v.height).coerceAtLeast(1).toFloat()
       floatXFraction=(v.x/maxX).coerceIn(0f,1f)
       floatYFraction=(v.y/maxY).coerceIn(0f,1f)
      } else {controlsOpen=!controlsOpen;updateControls()}
      return true
     }
     else -> return true
    }
   }
  })
  setContentView(root)
  layoutForOrientation()
  web.loadUrl("https://www.youtube.com/")
 }
 private fun landscape()=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
 private fun detach(view:View) {(view.parent as? android.view.ViewGroup)?.removeView(view)}
 private fun layoutForOrientation() {
  detach(navigation);detach(web);detach(footer);detach(handle);detach(quickRow);detach(drawer);detach(panel)
  root.removeAllViews()
  drawer.removeAllViews()
  panel.removeAllViews()
  if(landscape()) {
   panel.addView(web,FrameLayout.LayoutParams(-1,-1))
   drawer.setPadding(dp(10),dp(10),dp(10),dp(10))
   drawer.background=shape(Color.rgb(24,29,48),18)
   drawer.addView(navigation,LinearLayout.LayoutParams(-1,dp(56)))
   drawer.addView(footer,LinearLayout.LayoutParams(-1,-2))
   panel.addView(drawer,FrameLayout.LayoutParams(dp(320),-1,Gravity.RIGHT))
   handle.text="♫"
   panel.addView(handle,FrameLayout.LayoutParams(dp(54),dp(54),Gravity.TOP or Gravity.LEFT))
   panel.addView(quickRow,FrameLayout.LayoutParams(dp(248),dp(48),Gravity.TOP or Gravity.RIGHT).apply {topMargin=dp(10);rightMargin=dp(10)})
   panel.post {
    val maxX=(panel.width-handle.width).coerceAtLeast(0).toFloat()
    val maxY=(panel.height-handle.height).coerceAtLeast(0).toFloat()
    handle.x=maxX*floatXFraction
    handle.y=maxY*floatYFraction
   }
   root.addView(panel,LinearLayout.LayoutParams(-1,0,1f))
  } else {
   root.addView(navigation,LinearLayout.LayoutParams(-1,-2))
   // Keep the control handle in a stable overlay, independent of footer visibility.
   panel.addView(web,FrameLayout.LayoutParams(-1,-1))
   panel.addView(handle,FrameLayout.LayoutParams(dp(138),dp(44),Gravity.RIGHT or Gravity.BOTTOM).apply {
    rightMargin=dp(12)
    bottomMargin=dp(12)
   })
   panel.addView(quickRow,FrameLayout.LayoutParams(dp(248),dp(48),Gravity.TOP or Gravity.RIGHT).apply {topMargin=dp(8);rightMargin=dp(8)})
   root.addView(panel,LinearLayout.LayoutParams(-1,0,1f))
   root.addView(footer,LinearLayout.LayoutParams(-1,-2))
  }
  updateControls()
  root.requestApplyInsets()
  applySystemBars()
 }
 private fun updateControls() {
  handle.visibility=View.VISIBLE
  quickRow.visibility=if(controlsOpen)View.GONE else View.VISIBLE
  if(landscape()) {
   navigation.visibility=View.VISIBLE
   drawer.visibility=if(controlsOpen)View.VISIBLE else View.GONE
   handle.text=if(controlsOpen)"×" else "♫"
   handle.contentDescription=if(controlsOpen)"收起卡拉 OK 控制" else "展開卡拉 OK 控制"
   quickRow.bringToFront()
   handle.bringToFront()
  } else {
   navigation.visibility=if(immersive)View.GONE else View.VISIBLE
   footer.visibility=if(controlsOpen)View.VISIBLE else View.GONE
   handle.visibility=View.VISIBLE
   handle.text=if(controlsOpen)"⌄  收起控制" else "♫  卡拉 OK"
   handle.contentDescription=handle.text
   quickRow.bringToFront()
  }
 }
 override fun onConfigurationChanged(newConfig:Configuration) {
  super.onConfigurationChanged(newConfig)
  layoutForOrientation()
 }
 private fun applySystemBars() {
  val hide=immersive || customVideo!=null
  @Suppress("DEPRECATION")
  window.decorView.systemUiVisibility=if(hide)
   (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
   else 0
  fullscreen.text=if(immersive)"⛶  離開全螢幕" else "⛶  全螢幕"
  quickFullscreen.text=if(immersive)"⛶ 離開全螢幕" else "⛶ 全螢幕"
 }
 private fun closeCustomVideo() {
  val view=customVideo ?: return
  (view.parent as? ViewGroup)?.removeView(view)
  customVideo=null
  val callback=customVideoCallback
  customVideoCallback=null
  callback?.onCustomViewHidden()
  applySystemBars()
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
  enabled=false;toggle.text="開啟卡拉 OK";quickToggle.text="開啟卡拉 OK"
  web.loadUrl(target)
  (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
  address.clearFocus()
 }
 private fun setKaraoke(turnOn:Boolean) {
  val id=++requestId
  val strength=if(turnOn)(100-retain)/100.0 else 0.0
  val bassAmount=bass/100.0
  val output=volume/100.0
  val script="""(function(){
   try {
    const media=Array.from(document.querySelectorAll('video,audio'));
    if(!media.length)return JSON.stringify({ok:false,reason:'no-accessible-media'});
    const states=window.__karaokeStates||(window.__karaokeStates=new Map());
    let changed=0;const errors=[];
    for(const v of media) {
     try {
      let k=states.get(v);
      if(!k) {
       const ctx=new (window.AudioContext||window.webkitAudioContext)();
       const src=ctx.createMediaElementSource(v);
       const split=ctx.createChannelSplitter(2),merge=ctx.createChannelMerger(2);
       src.connect(split);
       // Mid/side: S=(L-R)/2. The side signal removes centered vocals
       // but also removes centered instruments. This is not source separation.
       [[0,0,1],[1,0,-1],[1,1,1],[0,1,-1]].forEach(function(t){
        const g=ctx.createGain();g.gain.value=t[2];
        split.connect(g,t[0]);g.connect(merge,0,t[1]);
       });
       const sideLow=ctx.createBiquadFilter(),sideHigh=ctx.createBiquadFilter();
       const originalLow=ctx.createBiquadFilter(),originalHigh=ctx.createBiquadFilter();
       for(const f of [sideLow,originalLow]){f.type='lowpass';f.frequency.value=180;f.Q.value=.707;}
       for(const f of [sideHigh,originalHigh]){f.type='highpass';f.frequency.value=180;f.Q.value=.707;}
       const dry=ctx.createGain(),wet=ctx.createGain();
       const lowOriginal=ctx.createGain(),lowSide=ctx.createGain();
       const out=ctx.createGain();
       // All paths feed a single output gain to prevent accidental double volume.
       src.connect(originalHigh);originalHigh.connect(dry);dry.connect(out);
       merge.connect(sideHigh);sideHigh.connect(wet);wet.connect(out);
       src.connect(originalLow);originalLow.connect(lowOriginal);lowOriginal.connect(out);
       merge.connect(sideLow);sideLow.connect(lowSide);lowSide.connect(out);
       out.connect(ctx.destination);
       k={ctx,dry,wet,lowOriginal,lowSide,out};
       states.set(v,k);
      }
      const t=k.ctx.currentTime,alpha=.035;
      const strength=__STRENGTH__,bass=__BASS__,volume=__VOLUME__;
      // Above 180Hz crossfade original with side signal.
      // Below 180Hz retain the center according to the bass slider.
      // Smooth all changes to avoid clicks.
      k.dry.gain.setTargetAtTime(1-strength,t,alpha);
      k.wet.gain.setTargetAtTime(strength,t,alpha);
      k.lowOriginal.gain.setTargetAtTime(1-strength+strength*bass,t,alpha);
      k.lowSide.gain.setTargetAtTime(strength*(1-bass),t,alpha);
      k.out.gain.setTargetAtTime(Math.min(1,volume),t,alpha);
      k.ctx.resume();changed++;
     }catch(e){errors.push(String(e));}
    }
    return JSON.stringify({ok:changed>0,changed,total:media.length,errors:errors.slice(0,2)});
   }catch(e){return JSON.stringify({ok:false,reason:String(e)});}
  })()""".replace("__STRENGTH__",strength.toString()).replace("__BASS__",bassAmount.toString()).replace("__VOLUME__",output.toString())
  web.evaluateJavascript(script) {raw->
   if(id!=requestId)return@evaluateJavascript
   try {
    val result=JSONObject(org.json.JSONTokener(raw).nextValue() as String)
    if(result.optBoolean("ok")) {
     enabled=turnOn
     toggle.text=if(enabled)"關閉卡拉 OK" else "開啟卡拉 OK"
     quickToggle.text=toggle.text
     toggle.background=shape(if(enabled)Color.rgb(60,69,94) else accent,14)
     toggle.setTextColor(if(enabled)Color.WHITE else background)
     status.text=if(enabled)"DSP 已套用 · 效果依歌曲立體聲混音而異" else "已恢復原始聲音"
    } else {
     enabled=false
     toggle.text="開啟卡拉 OK"
     quickToggle.text=toggle.text
     status.text="無法處理此頁面音訊："+result.optString("reason",result.optString("errors"))
    }
   }catch(e:Exception){status.text="音訊控制失敗，請重新播放再試"}
  }
 }
 @Deprecated("Deprecated in Java")
 override fun onBackPressed() {
  when {
   customVideo!=null -> closeCustomVideo()
   immersive -> {immersive=false;applySystemBars();layoutForOrientation()}
   web.canGoBack() -> web.goBack()
   else -> super.onBackPressed()
  }
 }
 override fun onDestroy() {closeCustomVideo();web.destroy();super.onDestroy()}
}
