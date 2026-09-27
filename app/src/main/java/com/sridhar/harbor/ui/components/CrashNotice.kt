package com.sridhar.harbor.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.sridhar.harbor.CrashGuard
import com.sridhar.harbor.R

/** Shown once after the app closed unexpectedly: honest, calm, and gives the user something to report. */
@Composable
fun CrashNotice() {
    var report by remember { mutableStateOf(CrashGuard.takeLastCrash()) }
    val clip = LocalClipboardManager.current
    val r = report ?: return
    AlertDialog(
        onDismissRequest = { report = null },
        title = { Text(stringResource(R.string.crash_title)) },
        text = { Text(stringResource(R.string.crash_body)) },
        confirmButton = { TextButton({ clip.setText(AnnotatedString(r)); report = null }) { Text(stringResource(R.string.crash_copy)) } },
        dismissButton = { TextButton({ report = null }) { Text(stringResource(R.string.dismiss)) } },
    )
}
