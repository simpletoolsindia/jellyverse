package com.sridhar.harbor.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sridhar.harbor.AppLocale
import com.sridhar.harbor.R
import com.sridhar.harbor.ui.theme.Harbor

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** English / தமிழ் / system switch. Each option is written in its own language so anyone can find theirs. */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val ctx = LocalContext.current
    val current = remember { AppLocale.current(ctx) }
    Column(modifier) {
        if (showTitle) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Translate, null, tint = Harbor.TextDim)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.language_title), style = MaterialTheme.typography.labelLarge, color = Harbor.TextDim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLocale.options.forEach { o ->
                val label = if (o.tag.isEmpty()) stringResource(R.string.language_system) else o.nativeName
                var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                FilterChip(
                    selected = current == o.tag,
                    onClick = { if (current != o.tag) ctx.activity()?.let { AppLocale.set(it, o.tag) } },
                    label = { Text(label) },
                    // TV: a clear white ring on the focused chip (Material's default is nearly invisible on dark).
                    modifier = Modifier.testTag("lang_${o.tag.ifEmpty { "system" }}")
                        .onFocusChanged { focused = it.isFocused },
                    border = if (focused) androidx.compose.foundation.BorderStroke(3.dp, Harbor.Fg) else null,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Harbor.Violet, selectedLabelColor = Color.White),
                )
            }
        }
    }
}
