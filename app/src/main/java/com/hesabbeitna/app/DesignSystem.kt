@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.hesabbeitna.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import android.os.Build
import android.view.WindowManager

object Brand {
    val Orange=Color(0xFFEE9451); val Green=Color(0xFF26734D); val Ivory=Color(0xFFFFF8EF); val Brown=Color(0xFF482623)
    val Error=Color(0xFFAD3038); val Warning=Color(0xFF815000)
    val Space4=4.dp; val Space8=8.dp; val Space12=12.dp; val Space16=16.dp; val Space24=24.dp; val Space32=32.dp
    val Card=RoundedCornerShape(24.dp); val Input=RoundedCornerShape(16.dp); const val Motion=180
}
private val LightPalette=lightColorScheme(
    primary=Brand.Green,onPrimary=Color.White,primaryContainer=Color(0xFFE1EFE4),onPrimaryContainer=Color(0xFF184A32),
    secondary=Brand.Orange,onSecondary=Brand.Brown,secondaryContainer=Color(0xFFFFE7D3),onSecondaryContainer=Brand.Brown,
    tertiary=Brand.Warning,onTertiary=Color.White,background=Brand.Ivory,onBackground=Brand.Brown,
    surface=Color(0xFFFFFDFA),onSurface=Brand.Brown,surfaceVariant=Color(0xFFF5EDE3),onSurfaceVariant=Color(0xFF745951),
    outline=Color(0xFF987D72),outlineVariant=Color(0xFFE7D9CA),
    surfaceDim=Color(0xFFF5EDE3),surfaceBright=Color(0xFFFFFDFA),surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFFFFDFA),surfaceContainer=Color(0xFFF5EDE3),surfaceContainerHigh=Color(0xFFF5EDE3),surfaceContainerHighest=Color(0xFFF0E3D5),inverseSurface=Brand.Brown,inverseOnSurface=Brand.Ivory,inversePrimary=Color(0xFF97D5AB),
    error=Brand.Error,onError=Color.White,errorContainer=Color(0xFFFFE8E8),onErrorContainer=Color(0xFF7B1823))
private val DarkPalette=darkColorScheme(
    primary=Color(0xFF97D5AB),onPrimary=Color(0xFF103820),primaryContainer=Color(0xFF254733),onPrimaryContainer=Color(0xFFD8F3E0),
    secondary=Brand.Orange,onSecondary=Brand.Brown,secondaryContainer=Color(0xFF58392B),onSecondaryContainer=Color(0xFFFFDCC2),
    tertiary=Color(0xFFF6C779),onTertiary=Color(0xFF482E00),background=Color.Black,onBackground=Color(0xFFF5F5F5),
    surface=Color(0xFF111111),onSurface=Color(0xFFF5F5F5),surfaceVariant=Color(0xFF191919),onSurfaceVariant=Color(0xFFB8B8B8),
    outline=Color(0xFF888888),outlineVariant=Color(0xFF292929),error=Color(0xFFFFB3B8),onError=Color(0xFF590D1C),
    surfaceDim=Color.Black,surfaceBright=Color(0xFF292929),surfaceContainerLowest=Color.Black,surfaceContainerLow=Color(0xFF111111),surfaceContainer=Color(0xFF191919),surfaceContainerHigh=Color(0xFF222222),surfaceContainerHighest=Color(0xFF292929),inverseSurface=Color(0xFFF5F5F5),inverseOnSurface=Color(0xFF111111),inversePrimary=Brand.Green,
    errorContainer=Color(0xFF502129),onErrorContainer=Color(0xFFFFDADC))
private val ArabicFont=FontFamily(Font(R.font.noto_sans_arabic))
private fun type(size:Int,line:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=ArabicFont,fontWeight=weight,fontSize=size.sp,lineHeight=line.sp)
private val HouseTypography=Typography(
    displaySmall=type(34,48,FontWeight.Bold),headlineLarge=type(28,40,FontWeight.Bold),headlineMedium=type(24,36,FontWeight.Bold),
    headlineSmall=type(21,32,FontWeight.Bold),titleLarge=type(19,30,FontWeight.SemiBold),titleMedium=type(16,26,FontWeight.SemiBold),
    titleSmall=type(14,24,FontWeight.SemiBold),bodyLarge=type(16,27),bodyMedium=type(14,24),bodySmall=type(13,22),
    labelLarge=type(14,24,FontWeight.SemiBold),labelMedium=type(12,20,FontWeight.SemiBold),labelSmall=type(11,18))
@Composable fun HouseTheme(dark:Boolean=isSystemInDarkTheme(),content:@Composable ()->Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme=if(dark)DarkPalette else LightPalette,typography=HouseTypography,
            shapes=Shapes(extraSmall=Brand.Input,small=Brand.Input,medium=Brand.Input,large=Brand.Card,extraLarge=RoundedCornerShape(32.dp))) {
            // MaterialTheme does not supply LocalContentColor to ordinary Text/Box content.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content=content)
        }
    }
}
@Composable fun Page(content:@Composable ColumnScope.()->Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side=if(maxWidth<360.dp)16.dp else 20.dp
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=side,vertical=16.dp),
            verticalArrangement=Arrangement.spacedBy(Brand.Space24),content=content)
    }
}
@Composable fun Panel(title:String?=null,content:@Composable ColumnScope.()->Unit) {
    LiquidSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(title!=null)Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics{heading()});content()
        }
    }
}
@Composable fun Hint(text:String){Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable fun ScreenTitle(title:String,subtitle:String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.semantics{heading()});subtitle?.let{Hint(it)}
    }
}
@Composable fun AmountLine(label:String,amount:Long,color:Color=MaterialTheme.colorScheme.primary) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Hint(label);Text(money(amount),style=MaterialTheme.typography.headlineMedium,color=color)}
}
@Composable fun Mascot(size:Dp=88.dp){Image(painterResource(R.drawable.brand_cat),"قطة Meow Budget تحتضن محفظة الادخار",Modifier.size(size).clip(Brand.Card))}
@Composable fun Empty(text:String="لا توجد عمليات مسجلة في هذه الفترة") {
    Panel{Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Mascot(72.dp);Text(text,style=MaterialTheme.typography.titleMedium);Hint("ابدأ ببياناتك الحقيقية، أو غيّر الفترة والتصفية")
    }}
}
@Composable fun Field(value:String,change:(String)->Unit,label:String,numeric:Boolean=false,secret:Boolean=false,error:String?=null) {
    OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=Brand.Input,
        singleLine=true,isError=error!=null,supportingText=if(error!=null){{Text(error)}}else null,
        keyboardOptions=KeyboardOptions(keyboardType=when{secret->KeyboardType.Password;numeric->KeyboardType.Decimal;else->KeyboardType.Text},imeAction=ImeAction.Next),
        textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=if(numeric||label.contains("YYYY"))TextDirection.Ltr else TextDirection.Content),
        visualTransformation=if(secret)PasswordVisualTransformation()else VisualTransformation.None,
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedContainerColor=MaterialTheme.colorScheme.surface))
}
@Composable fun Choice(label:String,current:String,options:List<Pair<String,String>>,select:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=Brand.Input,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),contentPadding=PaddingValues(16.dp)) {
        Column(Modifier.weight(1f)){Hint(label);Text(options.firstOrNull{it.first==current}?.second?:"اختر",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurface)};ToolIcon("down")
    }
    if(expanded)ModalBottomSheet(onDismissRequest={expanded=false},containerColor=Color.Transparent) {
        FrostedWindow()
        LiquidSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal=20.dp)) {
            ScreenTitle(label)
            options.forEach{option->TextButton(onClick={select(option.first);expanded=false},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=Brand.Input) {
                Text(option.second,modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurface);if(current==option.first)ToolIcon("check")
            };HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)};Spacer(Modifier.height(24.dp))
        }
        }
    }
}
@Composable fun DialogForm(title:String,dismiss:()->Unit,liquid:Boolean=LocalAppearance.current.glass,content:@Composable ColumnScope.()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        FrostedWindow()
        val body:@Composable ()->Unit = {
            Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(liquid)Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Box(Modifier.width(40.dp).height(4.dp).clip(Brand.Input).background(MaterialTheme.colorScheme.outline.copy(alpha=.45f)))}
                Row(verticalAlignment=Alignment.CenterVertically) {
                    if(liquid){Mascot(36.dp);Spacer(Modifier.width(10.dp))}
                    Text(title,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f).semantics{heading()})
                    IconButton(onClick=dismiss,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics{contentDescription="إغلاق"}){ToolIcon("close")}
                };content();TextButton(onClick=dismiss,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("إغلاق")}
            }
        }
        if(liquid)Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),contentAlignment=Alignment.BottomCenter) {
            LiquidSurface(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp),prominent=true,content=body)
        }else GlassSurface(Modifier.fillMaxWidth().padding(horizontal=12.dp).safeDrawingPadding().imePadding(),content=body)
    }
}
@Composable fun ErrorText(error:String?){if(error!=null)Surface(shape=Brand.Input,color=MaterialTheme.colorScheme.errorContainer) {
    Text("تنبيه: $error",color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.bodySmall,
        modifier=Modifier.fillMaxWidth().padding(12.dp).semantics{liveRegion=LiveRegionMode.Polite})
}}
@Composable fun PrimaryAction(label:String,onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier){Button(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=56.dp),shape=Brand.Input){Text(label)}}
@Composable fun QuickLink(title:String,detail:String,icon:String,click:()->Unit) {
    LiquidQuickLink(title,detail,icon,click)
}
@Composable fun BrandHeader(back:(()->Unit)?=null,search:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Mascot(44.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleLarge,
                modifier=Modifier.testTag("app-name").semantics{heading()})
            Hint("Meow معاك، وحسابك واضح")
        }
        if(back!=null) TextButton(onClick=back,modifier=Modifier.heightIn(min=48.dp)){Text("رجوع")}
        if(search!=null)IconButton(onClick=search,modifier=Modifier.testTag("global-search").semantics{contentDescription="البحث في كل التطبيق"}){ToolIcon("search")}
    }
}
/** Android 12+ blurs the real content behind modal windows when the device supports it. */
@Composable fun FrostedWindow() {
    val window=(LocalView.current.parent as? DialogWindowProvider)?.window
    val enabled=LocalAppearance.current.glass&&!LocalAppearance.current.reduceEffects
    DisposableEffect(window,enabled) {
        if(Build.VERSION.SDK_INT>=31&&window!=null) {
            val oldRadius=window.attributes.blurBehindRadius
            val hadFlag=window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND!=0
            if(enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes=window.attributes.apply{blurBehindRadius=32}
            } else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            onDispose {
                window.attributes=window.attributes.apply{blurBehindRadius=oldRadius}
                if(!hadFlag)window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        } else onDispose {}
    }
}
/** Soft lighting is blurred independently so labels and amounts remain sharp. */
@Composable fun GlassSurface(modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    LiquidSurface(modifier,content=content)
}
@Composable fun GlassNavigation(screen:String,select:(String)->Unit) {
    val items=listOf(Triple("home","الرئيسية","home"),Triple("transactions","العمليات","list"),Triple("analytics","التحليلات","chart"),Triple("plan","الخطة","budget"),Triple("more","المزيد","more"))
    GlassSurface(Modifier.navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp).fillMaxWidth()) {
        Row(Modifier.padding(4.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            items.forEach{(key,label,icon)->
                val selected=screen==key||(key=="more"&&screen in listOf("settings","accounts","budget","dues"))
                val color by animateColorAsState(if(selected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,tween(Brand.Motion),label="navigation")
                Surface(onClick={select(key)},modifier=Modifier.weight(1f).heightIn(min=64.dp).testTag("nav-$key").semantics{this.selected=selected;role=Role.Tab},shape=RoundedCornerShape(20.dp),color=color) {
                    Column(Modifier.padding(vertical=8.dp,horizontal=2.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        ToolIcon(icon,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label,style=MaterialTheme.typography.labelMedium,color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@Composable fun ToolIcon(kind:String,color:Color=MaterialTheme.colorScheme.primary,size:Dp=24.dp) {
    if(kind=="cat") { Image(painterResource(R.drawable.brand_cat),null,Modifier.size(28.dp));return }
    Canvas(Modifier.size(size)) {
        val s=size.width/24f
        fun line(x:Float,y:Float,xx:Float,yy:Float){drawLine(color,Offset(x*s,y*s),Offset(xx*s,yy*s),1.8f*s,StrokeCap.Round)}
        fun path(vararg p:Float){val shape=Path();shape.moveTo(p[0]*s,p[1]*s);for(i in 2 until p.size step 2)shape.lineTo(p[i]*s,p[i+1]*s);drawPath(shape,color,style=Stroke(1.8f*s,cap=StrokeCap.Round))}
        when(kind){
            "home"->{path(3f,11f,12f,3f,21f,11f);path(5f,10f,5f,21f,10f,21f,10f,15f,14f,15f,14f,21f,19f,21f,19f,10f)}
            "list"->{for(y in listOf(6f,12f,18f)){line(8f,y,21f,y);drawCircle(color,1.2f*s,Offset(3f*s,y*s))}}
            "chart"->{line(4f,20f,21f,20f);line(6f,16f,6f,11f);line(12f,16f,12f,5f);line(18f,16f,18f,8f)}
            "settings"->{for(y in listOf(6f,12f,18f))line(3f,y,21f,y);for((x,y)in listOf(8f to 6f,16f to 12f,10f to 18f))drawCircle(color,2.5f*s,Offset(x*s,y*s),style=Stroke(1.8f*s))}
            "cart"->{path(2f,4f,5f,4f,8f,16f,19f,16f,22f,7f,6f,7f);drawCircle(color,1.5f*s,Offset(9f*s,21f*s));drawCircle(color,1.5f*s,Offset(18f*s,21f*s))}
            "health"->{path(3f,8f,21f,8f,21f,21f,3f,21f,3f,8f);path(8f,8f,8f,4f,16f,4f,16f,8f);line(12f,11f,12f,18f);line(8f,14.5f,16f,14.5f)}
            "book"->{path(12f,5f,3f,3f,3f,19f,12f,21f,21f,19f,21f,3f,12f,5f,12f,21f)}
            "car"->{path(3f,18f,3f,11f,6f,5f,18f,5f,21f,11f,21f,18f,3f,18f);line(3f,11f,21f,11f);line(5f,18f,5f,21f);line(19f,18f,19f,21f);line(6f,14f,8f,14f);line(16f,14f,18f,14f)}
            "star"->{path(12f,2f,15f,8f,22f,9f,17f,14f,18f,21f,12f,18f,6f,21f,7f,14f,2f,9f,9f,8f,12f,2f)}
            "shirt"->{path(8f,3f,3f,6f,1f,12f,6f,13f,6f,21f,18f,21f,18f,13f,23f,12f,21f,6f,16f,3f,14f,6f,10f,6f,8f,3f)}
            "bill"->{path(5f,2f,19f,2f,19f,22f,16f,20f,12f,22f,8f,20f,5f,22f,5f,2f);line(8f,7f,16f,7f);line(8f,11f,16f,11f);line(8f,15f,13f,15f)}
            "wallet"->{path(3f,7f,3f,20f,21f,20f,21f,7f,3f,7f,17f,3f,17f,7f);path(21f,11f,15f,11f,15f,16f,21f,16f)}
            "calendar"->{path(3f,6f,21f,6f,21f,21f,3f,21f,3f,6f);line(3f,10f,21f,10f);line(8f,3f,8f,8f);line(16f,3f,16f,8f)}
            "budget"->{drawCircle(color,9f*s,Offset(12f*s,12f*s),style=Stroke(1.8f*s));path(12f,3f,12f,12f,21f,12f)}
            "plus"->{line(4f,12f,20f,12f);line(12f,4f,12f,20f)}
            "search"->{drawCircle(color,7f*s,Offset(10f*s,10f*s),style=Stroke(1.8f*s));line(15f,15f,22f,22f)}
            "more"->{for(x in listOf(5f,12f,19f))drawCircle(color,1.8f*s,Offset(x*s,12f*s))}
            "transfer"->{path(3f,7f,21f,7f,17f,3f);path(21f,17f,3f,17f,7f,21f)}
            "shield"->{path(12f,2f,3f,6f,4f,16f,12f,22f,20f,16f,21f,6f,12f,2f);path(8f,12f,11f,15f,17f,9f)}
            "close"->{line(6f,6f,18f,18f);line(18f,6f,6f,18f)}
            "down"->path(6f,9f,12f,15f,18f,9f)
            "check"->path(4f,12f,9f,17f,20f,6f)
            else->path(9f,5f,16f,12f,9f,19f)
        }
    }
}

/** Small brand companion: one quiet message rather than a blocking assistant. */
@Composable fun MeowMessage(title:String,detail:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Mascot(52.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium);Hint(detail)
        }
    }
}
