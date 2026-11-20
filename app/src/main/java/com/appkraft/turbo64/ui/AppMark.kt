package com.appkraft.turbo64.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appkraft.turbo64.R

/** The Turbo64 mark, the same picture as the launcher icon: a dark cube with a four-colour top and "64" on its face. */
@Composable
fun AppMark(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.app_mark), contentDescription = null, modifier = modifier.size(size))
}

/** Mark + wordmark at the same height on one line. */
@Composable
fun Wordmark(height: Dp = 28.dp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AppMark(height)
        Spacer(Modifier.width(8.dp))
        Text("Turbo", color = TextPrimary, fontSize = (height.value * 0.68f).sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
        Text("64", color = Accent, fontSize = (height.value * 0.68f).sp, fontWeight = FontWeight.Bold, fontFamily = AppFont)
    }
}
