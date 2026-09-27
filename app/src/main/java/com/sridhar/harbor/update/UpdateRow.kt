package com.sridhar.harbor.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.sridhar.harbor.R
import com.sridhar.harbor.ui.components.LocalContainer

/** Status line for the "Check for updates" setting (null in Play builds, which Play keeps updated). */
@Composable
fun updateStatus(): String? {
    val updater = LocalContainer.current.updater
    if (!updater.enabled) return null
    val s by updater.state.collectAsState()
    return when (val st = s) {
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, updater.currentVersion)
        is UpdateState.Failed -> stringResource(R.string.update_failed, st.message)
        is UpdateState.Available -> stringResource(R.string.update_available, st.info.version)
        else -> stringResource(R.string.update_current, updater.currentVersion)
    }
}
