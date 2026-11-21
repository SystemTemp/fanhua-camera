package com.fanhua.camera.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fanhua.camera.R
import com.fanhua.camera.camera.compose.CompositionGuide
import com.fanhua.camera.camera.compose.CompositionGuideLoader
import com.fanhua.camera.camera.compose.SmartCompositionCatalog
import com.fanhua.camera.ui.theme.Leica
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 智能构图参考图叠加层。
 *
 * 把选中的构图样张按画幅等比铺在预览上（Fit 保证样张完整可见），
 * [alpha] 控制透明度，让用户能同时看到真实画面和参考构图。
 */
@Composable
fun CompositionGuideOverlay(
    guideId: Int?,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val guide = SmartCompositionCatalog.find(guideId) ?: return
    val context = LocalContext.current
    val loader = remember { CompositionGuideLoader(context) }
    var bitmap by remember(guide.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(guide.id) {
        bitmap = withContext(Dispatchers.IO) { loader.load(guide) }
    }

    val bmp = bitmap ?: return
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = stringResource(R.string.composition_guide_overlay),
        contentScale = ContentScale.Fit,
        alpha = alpha,
        modifier = modifier.fillMaxSize()
    )
}

/**
 * 智能构图选择面板（风味相机式）。
 *
 * 顶部两个入口卡片：「AI 构图」（点闪光标图标进入检测构图）与
 * 「无」（清除模板）；下方是 63 个构图模板的两行网格预览，
 * 选中态红色描边。透明度档位固定在底部。
 */
@Composable
fun CompositionGuidePicker(
    selectedId: Int?,
    autoCompositionEnabled: Boolean,
    alpha: Float,
    onSelect: (Int?) -> Unit,
    onAlphaChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { CompositionGuideLoader(context) }
    val smart = remember { SmartCompositionCatalog.smartGuides() }
    val creative = remember { SmartCompositionCatalog.creativeGuides() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Leica.Ink.copy(alpha = 0.94f))
            .padding(vertical = 12.dp)
    ) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.composition_title),
                color = Leica.Text,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.composition_count, smart.size + creative.size),
                color = Leica.TextFaint,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.composition_close),
                color = Leica.TextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 入口卡片行：AI 构图 / 无
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EntryCard(
                title = stringResource(R.string.composition_entry),
                subtitle = stringResource(R.string.composition_entry_desc),
                selected = autoCompositionEnabled,
                leading = { AiBadge() },
                onClick = { onSelect(null) },
                modifier = Modifier.weight(1f)
            )
            EntryCard(
                title = stringResource(R.string.composition_none),
                subtitle = stringResource(R.string.composition_none_desc),
                selected = selectedId == null && !autoCompositionEnabled,
                leading = { NoneBadge() },
                onClick = { onSelect(null) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 模板网格：智能构图（1..48）+ 创意构图（49..63），各占一节
        SectionLabel(stringResource(R.string.composition_smart))
        GuideGrid(
            guides = smart,
            selectedId = selectedId,
            loader = loader,
            onSelect = onSelect
        )
        Spacer(modifier = Modifier.height(8.dp))
        SectionLabel(stringResource(R.string.composition_creative))
        GuideGrid(
            guides = creative,
            selectedId = selectedId,
            loader = loader,
            onSelect = onSelect
        )

        // 透明度：仅选中模板时显示
        if (selectedId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.composition_opacity),
                    color = Leica.TextFaint,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(10.dp))
                listOf(0.25f, 0.45f, 0.65f, 0.85f).forEach { value ->
                    val active = kotlin.math.abs(alpha - value) < 0.02f
                    Text(
                        text = "${(value * 100).toInt()}%",
                        color = if (active) Leica.Red else Leica.TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable { onAlphaChange(value) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/** AI 构图入口卡：左侧发光圆标 + 文案，选中态红色描边。 */
@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Leica.Grey.copy(alpha = 0.55f))
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) Leica.Red else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = if (selected) Leica.Red else Leica.Text,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Leica.TextFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

/** AI 构图徽标：径向光斑 + 中心点，模拟「检测中」的观感。 */
@Composable
private fun AiBadge() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Leica.Red.copy(alpha = 0.85f), Leica.Red.copy(alpha = 0.12f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Leica.White)
        )
    }
}

/** 「无」入口徽标：空圆环。 */
@Composable
private fun NoneBadge() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.5.dp, Leica.TextDim, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Leica.TextDim)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Leica.TextFaint,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
    )
}

/** 模板网格：4 列，每格是样张缩略 + 编号，选中红框。 */
@Composable
private fun GuideGrid(
    guides: List<CompositionGuide>,
    selectedId: Int?,
    loader: CompositionGuideLoader,
    onSelect: (Int?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(gridHeight(guides.size))
    ) {
        items(guides, key = { it.id }) { guide ->
            val selected = guide.id == selectedId
            var cover by remember(guide.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(guide.id) {
                cover = withContext(Dispatchers.IO) { loader.loadCover(guide) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.86f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black)
                        .border(
                            width = if (selected) 1.5.dp else 0.5.dp,
                            color = if (selected) Leica.Red else Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onSelect(if (selected) null else guide.id) }
                ) {
                    cover?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = guide.id.toString(),
                    color = if (selected) Leica.Red else Leica.TextFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** 网格高度：行数 = ceil(n / 4)，每行 ≈ 108dp。 */
private fun gridHeight(itemCount: Int): androidx.compose.ui.unit.Dp {
    val rows = (itemCount + 3) / 4
    return (rows * 108).dp
}
