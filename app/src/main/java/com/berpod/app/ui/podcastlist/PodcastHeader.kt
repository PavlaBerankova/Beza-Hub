package com.berpod.app.ui.podcastlist

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** The play button in the header: its label, icon and action. */
data class HeaderPlayButton(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Apple Podcasts-style show header: a block in the cover's colour with the large cover centred, the title,
 * the author and a play button. [topPadding] keeps the content below the transparent top app bar drawn over it.
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
    playButton: HeaderPlayButton?,
    onColorExtracted: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(start = 24.dp, end = 24.dp, top = topPadding + 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .shadow(elevation = 12.dp, shape = CoverShape)
                .clip(CoverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Podcasts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            if (imageUrl != null) {
                AsyncImage(
                    // Software bitmap so its pixels can be read for the header colour.
                    model = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { state -> onColorExtracted(averageColor(state.result.image.toBitmap())) },
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (author != null) {
            Text(
                text = author,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (playButton != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = playButton.onClick,
                colors = ButtonDefaults.buttonColors(containerColor = contentColor, contentColor = background),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Icon(playButton.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(playButton.label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private fun averageColor(bitmap: Bitmap): Color = Color(Bitmap.createScaledBitmap(bitmap, 1, 1, true).getPixel(0, 0))
