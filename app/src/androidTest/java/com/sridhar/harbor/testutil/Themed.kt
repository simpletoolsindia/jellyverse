package com.sridhar.harbor.testutil

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.sridhar.harbor.ui.theme.HarborTheme

/** Renders [content] inside the app theme, like every real screen. */
fun ComposeContentTestRule.setThemed(content: @Composable () -> Unit) = setContent { HarborTheme { Surface { content() } } }
