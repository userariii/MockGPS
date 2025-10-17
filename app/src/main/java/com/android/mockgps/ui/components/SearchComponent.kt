package com.android.mockgps.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import com.android.mockgps.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchComponent(
    modifier: Modifier = Modifier,
    onSearch: (text: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLight = !isSystemInDarkTheme()

    // Equal left/right reserve so the text’s center is the visual center
    val sideInset = 56.dp // 47.dp icon + 9.dp edge padding

    val searchBg = if (isLight) Color.Black else Color.White
    val searchFg = if (isLight) Color.White else Color.Black

    Box(modifier = modifier) {

        // TextField with centered typing and symmetric insets
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .align(Alignment.TopCenter),
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            maxLines = 1,
            textStyle = TextStyle.Default.copy(
                textAlign = TextAlign.Center,
                fontSize = 18.sp
            ),
            placeholder = {}, // custom overlay below
            prefix = { Spacer(Modifier.width(sideInset)) },
            suffix = { Spacer(Modifier.width(sideInset)) },
            colors = TextFieldDefaults.textFieldColors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(32.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onSearch(text)
                keyboardController?.hide()
            })
        )

        // Centered placeholder overlay
        if (text.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Search Location",
                    textAlign = TextAlign.Center,
                    color = if (isLight) Color.Black else Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp)
                )
            }
        }

        // Left app icon (unchanged size)
        Icon(
            painter = launcherPainter(),
            contentDescription = "App Icon",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(47.dp)
                .padding(start = 9.dp),
            tint = Color.Unspecified
        )

        // Right circular search button (forced circle via clip + background)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 9.dp)
                .size(39.dp)                  // exact diameter
                .clip(CircleShape)            // force circular shape
                .background(searchBg)         // paint the circle
                .clickable(
                    role = Role.Button,
                    indication = rememberRipple(bounded = true),
                    interactionSource = remember { MutableInteractionSource() }
                ) { onSearch(text) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "search",
                tint = searchFg
            )
        }
    }
}

@Composable
private fun launcherPainter(): Painter {
    val context = LocalContext.current
    val d: Drawable? = ResourcesCompat.getDrawable(
        context.resources,
        R.mipmap.ic_launcher,
        context.theme
    )
    return remember(d) {
        when {
            d is BitmapDrawable -> BitmapPainter(d.bitmap.asImageBitmap())
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable -> {
                val w = (d.intrinsicWidth.takeIf { it > 0 } ?: 128)
                val h = (d.intrinsicHeight.takeIf { it > 0 } ?: 128)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                d.setBounds(0, 0, canvas.width, canvas.height)
                d.draw(canvas)
                BitmapPainter(bmp.asImageBitmap())
            }
            else -> {
                val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                BitmapPainter(bmp.asImageBitmap())
            }
        }
    }
}

@Preview
@Composable
fun SearchComponentPreview() {
    SearchComponent(onSearch = {})
}
