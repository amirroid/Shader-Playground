package ir.amirroid.shading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val BgDeep = Color(0xFF0D0F14)
private val BgPanel = Color(0xFF141720)
private val BgEditor = Color(0xFF181B24)
private val BgGutter = Color(0xFF11131B)
private val Accent = Color(0xFF7C3AED)
private val RunGreen = Color(0xFF4ADE80)
private val ErrRed = Color(0xFFEF4444)
private val ErrBg = Color(0x18EF4444)
private val TextPrimary = Color(0xFFE2E8F0)
private val TextMuted = Color(0xFF4A5568)
private val LineNumCol = Color(0xFF374151)
private val DividerCol = Color(0xFF1F2433)
private val ChipBg = Color(0xFF1C2030)

@Composable
fun ShaderEditorScreen() {
    var shaderCode by rememberSaveable { mutableStateOf(ShaderRenderer.DEFAULT_FRAGMENT_SHADER) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var glView by remember { mutableStateOf<ShaderGLSurfaceView?>(null) }
    var isRunning by remember { mutableStateOf(false) }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun runShader() {
        isRunning = true
        glView?.updateShader(shaderCode) { result ->
            isRunning = false
            errorMessage = when (result) {
                is ShaderCompileResult.Success -> null
                is ShaderCompileResult.Error -> result.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(top = statusBarPadding, bottom = navBarPadding)
    ) {
        IdeTopBar(isRunning = isRunning, onRun = { runShader() })

        HorizontalDivider(color = DividerCol)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
                .padding(12.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, DividerCol, RoundedCornerShape(10.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx -> ShaderGLSurfaceView(ctx).also { glView = it } },
                modifier = Modifier.fillMaxSize()
            )
        }

        val uniforms = remember(shaderCode) {
            ShaderRenderer.extractUniforms(shaderCode)
        }
        glView?.let { view ->
            IdeParamPanel(renderer = view.renderer, uniforms = uniforms)
        }

        AnimatedVisibility(
            visible = errorMessage != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ConsoleError(message = errorMessage.orEmpty())
        }

        HorizontalDivider(color = DividerCol)

        IdeCodeEditor(
            code = shaderCode,
            onCodeChange = { shaderCode = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
        )
    }
}

@Composable
private fun HorizontalDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
fun IdeTopBar(isRunning: Boolean, onRun: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(ChipBg, RoundedCornerShape(6.dp))
                .border(1.dp, DividerCol, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Accent, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "fragment.glsl",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = TextPrimary
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onRun,
            enabled = !isRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = RunGreen,
                disabledContainerColor = RunGreen.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Running…",
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            } else {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Run", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeParamPanel(renderer: ShaderRenderer, uniforms: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val values: SnapshotStateMap<String, Float> = remember { mutableStateMapOf() }

    val arrowAngle by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(250),
        label = "arrow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgPanel)
            .animateContentSize(tween(250))
    ) {
        HorizontalDivider(color = DividerCol)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(Accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "UNIFORMS",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))

            val blink by rememberInfiniteTransition(label = "blink").animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "blinkAlpha"
            )
            if (expanded && uniforms.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    uniforms.forEach { name ->
                        val active = (values[name] ?: 0f) != 0f
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(
                                    if (active) Accent.copy(alpha = blink) else DividerCol,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }

            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(arrowAngle)
            )
        }

        if (expanded) {
            if (uniforms.isEmpty()) {
                Text(
                    "No adjustable uniforms",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    uniforms.forEach { name ->
                        val value = values[name] ?: 0f
                        UniformSlider(name, value, Modifier.width(78.dp)) {
                            values[name] = it
                            renderer.uniformValues[name] = it
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = DividerCol)
    }
}

@Composable
fun UniformSlider(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = modifier
            .background(ChipBg, RoundedCornerShape(8.dp))
            .border(1.dp, DividerCol, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Accent,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(1.dp))
        Text(
            "%.2f".format(value),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -1f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = DividerCol
            )
        )
    }
}

@Composable
fun ConsoleError(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrBg)
            .border(BorderStroke(1.dp, ErrRed.copy(alpha = 0.3f)))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "✕",
            color = ErrRed,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 1.dp, end = 8.dp)
        )
        Text(
            message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = ErrRed,
            lineHeight = 17.sp
        )
    }
}

@Composable
fun IdeCodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lineCount = code.lines().size
    val scrollState = rememberScrollState()

    Row(modifier = modifier.background(BgEditor)) {
        Column(
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
                .background(BgGutter)
                .border(BorderStroke(1.dp, DividerCol))
                .verticalScroll(scrollState)
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.End
        ) {
            repeat(maxOf(lineCount, 1)) { i ->
                Text(
                    text = "${i + 1}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = LineNumCol,
                    textAlign = TextAlign.End,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .width(34.dp)
                        .padding(end = 10.dp)
                )
            }
        }

        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 14.dp, horizontal = 12.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TextPrimary,
                lineHeight = 20.sp,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false
            ),
            cursorBrush = SolidColor(Accent),
        )
    }
}