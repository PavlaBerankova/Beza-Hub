package com.berpod.app.ui.podcastlist

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.res.stringResource
import com.berpod.app.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * Whether the transparent top bar still has header behind it: the header is the first list item and at least
 * the bar's height of it is left on screen. Otherwise episode rows would slide under white icons.
 */
fun isBarOverHeader(firstVisibleIndex: Int, scrollOffsetPx: Int, headerHeightPx: Int, barHeightPx: Int): Boolean =
    firstVisibleIndex == 0 && scrollOffsetPx <= headerHeightPx - barHeightPx

private val CoverShape = RoundedCornerShape(12.dp)
private val TagShape = RoundedCornerShape(50)

/** Shared height of the header's play button and round action buttons (also their touch target). */
private val ActionHeight = 48.dp

/** The play button in the header: its label, icon and action. */
data class HeaderPlayButton(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Apple Podcasts-style show header: a compact block in the cover's colour with the cover on the left and the
 * title, author and tags beside it, then a row with the play button and the tag / refresh actions. [topPadding] keeps the content below the transparent top app bar drawn over it.
 * [onColorExtracted] reports the cover's average colour once the image has loaded.
 */
@Composable
fun PodcastHeader(
    title: String,
    author: String?,
    imageUrl: String?,
    topPadding: Dp,
    background: Color,
    contentColor: Color,
    tags: List<String>,
    playButton: HeaderPlayButton?,
    isRefreshing: Boolean,
    onTagClick: () -> Unit,
    onRefresh: () -> Unit,
    onColorExtracted: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(elevation = 8.dp, shape = CoverShape)
                    .clip(CoverShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Podcasts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
                if (imageUrl != null) {
                    AsyncImage(
                        // Software bitmap so its pixels can be read for the header colour.
                        model = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { state -> coverColor(state.result.image.toBitmap())?.let(onColorExtracted) },
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (author != null) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(TagShape)
                                    .background(contentColor.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (playButton != null) {
                Button(
                    onClick = playButton.onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = contentColor, contentColor = background),
                    // Same width as the cover above it.
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.width(120.dp).height(ActionHeight)
                ) {
                    Icon(playButton.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(playButton.label, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
            }
            OutlinedIconButton(
                onClick = onTagClick,
                border = BorderStroke(1.5.dp, contentColor),
                modifier = Modifier.size(ActionHeight)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Label,
                    contentDescription = stringResource(R.string.action_manage_tags),
                    tint = contentColor
                )
            }
            Spacer(Modifier.width(10.dp))
            if (isRefreshing) {
                OutlinedIconButton(
                    onClick = {},
                    enabled = false,
                    border = BorderStroke(1.5.dp, contentColor),
                    modifier = Modifier.size(ActionHeight)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = contentColor, strokeWidth = 2.dp)
                }
            } else {
                OutlinedIconButton(
                    onClick = onRefresh,
                    border = BorderStroke(1.5.dp, contentColor),
                    modifier = Modifier.size(ActionHeight)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = contentColor)
                }
            }
        }
    }
}

private fun coverColor(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap).generate()
    return pickHeaderColor(
        vibrant = palette.vibrantSwatch?.rgb,
        darkVibrant = palette.darkVibrantSwatch?.rgb,
        muted = palette.mutedSwatch?.rgb,
        dominant = palette.dominantSwatch?.rgb
    )?.let(::Color)
}
