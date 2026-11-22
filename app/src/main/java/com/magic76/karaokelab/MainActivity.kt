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
 private lateinit var compactFooter:LinearLayout
 private lateinit var compactToggle:Button
 private lateinit var compactFullscreen:Button
 private lateinit var drawer:LinearLayout
 private lateinit var panel:FrameLayout
 private lateinit var handle:Button
 private lateinit var quickRow:LinearLayout
 private lateinit var quickToggle:Button
 private lateinit var quickFullscreen:Button
 private lateinit var fullscreen:Button
 private lateinit var bassSlider:SeekBar
 private lateinit var bassPercent:TextView
 private var immersive=false
 private var customVideo:View?=null
 private var customVideoCallback:WebChromeClient.CustomViewCallback?=null
 private var floatYFraction=0.5f
 private var floatXFraction=1f
 private var controlsOpen=true
 private var enabled=false
 private var retain=0
 private var dspMode=1
 private lateinit var modeButton:Button
 private var bass=75
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
     compactToggle.text="開啟卡拉 OK"
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
  modeButton=styledButton("DSP：分頻保護").apply {
   setOnClickListener {
    dspMode=1-dspMode
    text=if(dspMode==0)"DSP：純差分（診斷）" else "DSP：分頻保護"
    if(enabled)setKaraoke(true)
   }
  }
  footer.addView(modeButton,LinearLayout.LayoutParams(-1,dp(42)).apply {topMargin=dp(6)})
  val vocalControl=addControl("人聲消除強度",100) {value->
   retain=100-value
   if(enabled)setKaraoke(true)
  }
  slider=vocalControl.first
  percent=vocalControl.second
  val bassControl=addControl("低音保留",bass) {value->bass=value;if(enabled)setKaraoke(true)}
  bassSlider=bassControl.first
  bassPercent=bassControl.second
  footer.addView(styledButton("收起控制").apply {setOnClickListener {controlsOpen=false;updateControls()}},
   LinearLayout.LayoutParams(-1,dp(38)).apply {topMargin=dp(4)})
  status=caption("播放音樂後，可開啟卡拉 OK 模式").apply {textSize=12f}
  footer.addView(status)
  compactFooter=LinearLayout(this).apply {
   orientation=LinearLayout.HORIZONTAL
   setPadding(dp(8),dp(5),dp(8),dp(5))
   background=shape(Color.rgb(25,30,48),14)
  }
  compactToggle=styledButton("開啟卡拉 OK",true).apply {
   textSize=12f
   setOnClickListener {setKaraoke(!enabled)}
  }
  compactFullscreen=styledButton("⛶ 全螢幕").apply {
   textSize=12f
   setOnClickListener {immersive=!immersive;if(immersive)controlsOpen=false;applySystemBars();layoutForOrientation()}
  }
  compactFooter.addView(compactToggle,LinearLayout.LayoutParams(0,dp(42),1f))
  compactFooter.addView(compactFullscreen,LinearLayout.LayoutParams(0,dp(42),1f).apply {leftMargin=dp(6)})
  compactFooter.addView(styledButton("調整 ▴").apply {
   textSize=12f
   setOnClickListener {controlsOpen=true;updateControls()}
  },LinearLayout.LayoutParams(dp(84),dp(42)).apply {leftMargin=dp(6)})
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
  detach(navigation);detach(web);detach(footer);detach(compactFooter);detach(handle);detach(quickRow);detach(drawer);detach(panel)
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
   root.addView(compactFooter,LinearLayout.LayoutParams(-1,-2))
  }
  updateControls()
  root.requestApplyInsets()
  applySystemBars()
 }
 private fun updateControls() {
  handle.visibility=View.VISIBLE
  quickRow.visibility=if(landscape() && !controlsOpen)View.VISIBLE else View.GONE
  compactFooter.visibility=if(!landscape() && !controlsOpen)View.VISIBLE else View.GONE
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
   handle.visibility=View.GONE
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
  compactFullscreen.text=quickFullscreen.text
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
  enabled=false;toggle.text="開啟卡拉 OK";quickToggle.text="開啟卡拉 OK";compactToggle.text="開啟卡拉 OK"
  web.loadUrl(target)
  (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
  address.clearFocus()
 }
 private fun setKaraoke(turnOn:Boolean) {
  val id=++requestId
  val strength=if(turnOn)(100-retain)/100.0 else 0.0
  val bassAmount=bass/100.0
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
       // Side is (L-R)/2 on the left and (R-L)/2 on the right.
       [[0,0,.5],[1,0,-.5],[1,1,.5],[0,1,-.5]].forEach(function(t){
        const g=ctx.createGain();g.gain.value=t[2];
        split.connect(g,t[0]);g.connect(merge,0,t[1]);
       });
       const out=ctx.createGain();
       const dry=ctx.createGain(),wet=ctx.createGain();
       src.connect(dry);dry.connect(out);
       merge.connect(wet);wet.connect(out);
       // Exact complementary correction: low-original minus low-side.
       // Baseline mode sets correction to zero and uses no crossover.
       const lowOriginal=ctx.createBiquadFilter(),lowSide=ctx.createBiquadFilter();
       for(const f of [lowOriginal,lowSide]){f.type='lowpass';f.frequency.value=180;f.Q.value=.707;}
       const lowOrigGain=ctx.createGain(),lowSideGain=ctx.createGain();
       src.connect(lowOriginal);lowOriginal.connect(lowOrigGain);lowOrigGain.connect(out);
       merge.connect(lowSide);lowSide.connect(lowSideGain);lowSideGain.connect(out);
       // High-frequency protection is the complementary difference:
       // (original-side) - lowpass(original-side) above 5.2 kHz.
       // All correction paths use the same filter coefficients.
       const highOriginal=ctx.createBiquadFilter(),highSide=ctx.createBiquadFilter();
       for(const f of [highOriginal,highSide]){f.type='lowpass';f.frequency.value=5200;f.Q.value=.707;}
       const highOrigDirect=ctx.createGain(),highSideDirect=ctx.createGain();
       const highOrigLow=ctx.createGain(),highSideLow=ctx.createGain();
       src.connect(highOrigDirect);highOrigDirect.connect(out);
       merge.connect(highSideDirect);highSideDirect.connect(out);
       src.connect(highOriginal);highOriginal.connect(highOrigLow);highOrigLow.connect(out);
       merge.connect(highSide);highSide.connect(highSideLow);highSideLow.connect(out);
       out.connect(ctx.destination);
       k={ctx,dry,wet,lowOrigGain,lowSideGain,highOrigDirect,highSideDirect,highOrigLow,highSideLow,out};
       // Start in bypass to prevent loud transients before gains are configured.
       for(const name of ['wet','lowOrigGain','lowSideGain','highOrigDirect','highSideDirect','highOrigLow','highSideLow'])k[name].gain.value=0;
       k.dry.gain.value=1;k.out.gain.value=1;
       states.set(v,k);
      }
      const t=k.ctx.currentTime,alpha=.05;
      const strength=__STRENGTH__,bass=__BASS__,mode=__MODE__;
      const low=mode===1?strength*bass:0;
      const high=mode===1?strength*.35:0;
      function ramp(node,value){node.gain.setTargetAtTime(value,t,alpha);}
      ramp(k.dry,1-strength);ramp(k.wet,strength);
      ramp(k.lowOrigGain,low);ramp(k.lowSideGain,-low);
      ramp(k.highOrigDirect,high);ramp(k.highSideDirect,-high);
      ramp(k.highOrigLow,-high);ramp(k.highSideLow,high);
      ramp(k.out,__OUTPUT_GAIN__);
      k.ctx.resume();changed++;
     }catch(e){errors.push(String(e));}
    }
    return JSON.stringify({ok:changed>0,changed,total:media.length,errors:errors.slice(0,2)});
   }catch(e){return JSON.stringify({ok:false,reason:String(e)});}
  })()""".replace("__STRENGTH__",strength.toString()).replace("__BASS__",bassAmount.toString()).replace("__MODE__",dspMode.toString()).replace("__OUTPUT_GAIN__",(if(turnOn)0.8 else 1.0).toString())
  web.evaluateJavascript(script) {raw->
   if(id!=requestId)return@evaluateJavascript
   try {
    val result=JSONObject(org.json.JSONTokener(raw).nextValue() as String)
    if(result.optBoolean("ok")) {
     enabled=turnOn
     toggle.text=if(enabled)"關閉卡拉 OK" else "開啟卡拉 OK"
     quickToggle.text=toggle.text
     compactToggle.text=toggle.text
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
