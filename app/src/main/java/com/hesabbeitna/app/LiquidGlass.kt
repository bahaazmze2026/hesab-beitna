package com.hesabbeitna.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

/** Shared application material: dense readable plate, independent optical highlights and a polished rim.
 * Decorative blur never touches text. This does not claim Apple's native optical refraction. */
@Composable fun LiquidSurface(modifier: Modifier = Modifier, prominent: Boolean = false,
    content: @Composable () -> Unit) {
    val appearance = LocalAppearance.current
    val colors = MaterialTheme.colorScheme
    val dark = colors.background == Color.Black
    val glass = appearance.glass
    val effects = glass && !appearance.reduceEffects
    val shape = RoundedCornerShape(if (prominent) 32.dp else 26.dp)
    val base = if (glass) { if (dark) Color(0xFF111315) else Color(0xFFFFFCF7) } else colors.surface
    val rim = Brush.linearGradient(listOf(
        if (dark) Color.White.copy(alpha = if (glass) .32f else .12f) else Color.White,
        colors.outlineVariant.copy(alpha = .6f),
        if (dark) Color.White.copy(alpha = .10f) else colors.outlineVariant.copy(alpha = .65f)))
    Box(modifier.shadow(if (effects) 10.dp else 0.dp, shape, clip = false).clip(shape)
        .background(base).border(1.dp, rim, shape)) {
        if (effects) Canvas(Modifier.matchParentSize().blur(18.dp)) {
            drawCircle((if (dark) Color.White else Brand.Green).copy(alpha = if (dark) .06f else .09f),
                size.width * .38f, Offset(size.width * .12f, size.height * .8f))
            drawCircle(Brand.Orange.copy(alpha = if (dark) .045f else .12f),
                size.width * .3f, Offset(size.width * .92f, 0f))
        }
        if (glass) Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) .07f else .56f),
                Color.Transparent, Color.White.copy(alpha = if (dark) .015f else .12f)),
                start = Offset.Zero, end = Offset(size.width, size.height)))
            if (effects) {
                drawOval(Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) .08f else .35f), Color.Transparent),
                    center = Offset(size.width * .7f, -size.height * .35f), radius = size.width * .8f),
                    topLeft = Offset(-size.width * .15f, -size.height * .85f), size = androidx.compose.ui.geometry.Size(size.width * 1.4f, size.height * 1.15f))
                drawLine(Color.White.copy(alpha = if (dark) .23f else .86f), Offset(size.width * .10f, 1.dp.toPx()),
                    Offset(size.width * .72f, 1.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
        }
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Box(Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable fun LiquidPanel(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    LiquidSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable fun LiquidActionGrid(actions: List<HubAction>, selected: String? = null, click: (String) -> Unit) {
    actions.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { action ->
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                val reduce = LocalAppearance.current.reduceEffects
                val scale by animateFloatAsState(if (pressed && !reduce) .975f else 1f,
                    if (reduce) snap() else tween(140), label = "liquid-press")
                LiquidSurface(Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }) {
                    Surface(onClick = { click(action.key) }, interactionSource = source,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).testTag(action.key)
                            .semantics { role = Role.Button; if (selected != null) this.selected = selected == action.key },
                        shape = RoundedCornerShape(26.dp), color = if (selected == action.key) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .85f) else Color.Transparent,
                        contentColor = if (selected == action.key) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ToolIcon(action.icon); Text(action.title, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable fun LiquidNavigation(screen: String, select: (String) -> Unit) {
    val items = listOf(Triple("home", "الرئيسية", "home"), Triple("transactions", "العمليات", "list"),
        Triple("analytics", "التحليلات", "chart"), Triple("plan", "الخطة", "budget"), Triple("more", "المزيد", "more"))
    val selectedIndex = items.indexOfFirst { it.first == screen }.let { if (it < 0) 4 else it }
    val reduce = LocalAppearance.current.reduceEffects || !LocalAppearance.current.glass
    LiquidSurface(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().testTag("bottom-navigation")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(3.dp)) {
            val segment = maxWidth / 5
            val position by animateDpAsState(segment * selectedIndex,
                if (reduce) snap() else tween(220), label = "liquid-navigation")
            Box(Modifier.matchParentSize()) {
                Box(Modifier.offset(x = position).width(segment).fillMaxHeight().padding(2.dp)
                    .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .2f)), RoundedCornerShape(18.dp)))
            }
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, (key, label, icon) ->
                    val selected = index == selectedIndex
                    Surface(onClick = { select(key) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("nav-$key").semantics { this.selected = selected; role = Role.Tab },
                        shape = RoundedCornerShape(18.dp), color = Color.Transparent) {
                        Column(Modifier.padding(vertical = 3.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ToolIcon(icon, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, iconSize = 20.dp)
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun LiquidAmountField(value: String, change: (String) -> Unit, error: String?) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text("المبلغ — جنيه") },
        modifier = Modifier.fillMaxWidth(), shape = Brand.Card, singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Ltr),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = error != null,
        supportingText = if (error == null) null else { { Text(error) } },
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface))
}

@Composable fun LiquidQuickLink(title:String,detail:String,icon:String,click:()->Unit) {
    LiquidSurface(Modifier.fillMaxWidth()) {
        Surface(onClick=click,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp),color=Color.Transparent,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(26.dp)) {
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ToolIcon(icon)
                Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Hint(detail)}
                ToolIcon("back")
            }
        }
    }
}

/** One clickable glass plate; selection stays opaque enough to read in either theme. */
@Composable fun LiquidClickableSurface(onClick: () -> Unit, modifier: Modifier = Modifier,
    chosen: Boolean = false, content: @Composable () -> Unit) {
    LiquidSurface(Modifier.fillMaxWidth()) {
        Surface(onClick = onClick, modifier = modifier,
            shape = RoundedCornerShape(26.dp),
            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            border = if (chosen) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            content = content)
    }
}
