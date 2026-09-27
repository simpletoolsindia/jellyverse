package com.sridhar.harbor.testutil

import com.sridhar.harbor.data.ServerConfig
import com.sridhar.harbor.data.SettingsStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** A [SettingsStore] backed by an in-memory [ServerConfig]; `update` mutates it like DataStore would. */
fun fakeSettings(initial: ServerConfig): Pair<SettingsStore, MutableStateFlow<ServerConfig>> {
    val state = MutableStateFlow(initial)
    val store = mockk<SettingsStore>(relaxed = true)
    coEvery { store.current() } answers { state.value }
    coEvery { store.update(any()) } answers { state.value = firstArg<(ServerConfig) -> ServerConfig>()(state.value) }
    every { store.config } returns state
    return store to state
}
