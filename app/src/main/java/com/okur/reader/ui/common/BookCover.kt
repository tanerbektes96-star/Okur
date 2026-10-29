package com.okur.reader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.okur.reader.data.db.BookEntity
import com.okur.reader.ui.theme.CoverTitleStyle
import java.io.File

private val coverPalettes = listOf(
    Color(0xFF8C4A2F) to Color(0xFFC77B4E),
    Color(0xFF2F5D62) to Color(0xFF5E8B7E),
    Color(0xFF3D3B6E) to Color(0xFF6C5B9E),
    Color(0xFF6B2737) to Color(0xFFA4495C),
    Color(0xFF35524A) to Color(0xFF779C7C),
    Color(0xFF4A4E69) to Color(0xFF9A8C98),
    Color(0xFF7F5539) to Color(0xFFB08968),
    Color(0xFF1D3557) to Color(0xFF457B9D),
)

/** Kapak görseli; yoksa başlığa göre üretilmiş zarif bir kapak. */
@Composable
fun BookCover(book: BookEntity, modifier: Modifier = Modifier, corner: Int = 10) {
    val shape = RoundedCornerShape(corner.dp)
    Surface(
        modifier = modifier.aspectRatio(0.68f),
        shape = shape,
        shadowElevation = 3.dp,
        tonalElevation = 1.dp,
    ) {
        if (book.coverPath != null && File(book.coverPath).exists()) {
            AsyncImage(
                model = File(book.coverPath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
            )
        } else {
            GeneratedCover(book.title, book.author, book.format)
        }
    }
}

@Composable
private fun GeneratedCover(title: String, author: String, format: String) {
    val (a, b) = coverPalettes[(title.hashCode() and Int.MAX_VALUE) % coverPalettes.size]
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(a, b)))
            .padding(12.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .height(56.dp)
                .background(Color.White.copy(alpha = 0.5f))
        )
        Column(Modifier.fillMaxWidth().padding(start = 10.dp).align(Alignment.CenterStart)) {
            Text(title, style = CoverTitleStyle, color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(author, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            format,
            modifier = Modifier.align(Alignment.BottomEnd),
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
