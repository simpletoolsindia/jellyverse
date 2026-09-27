package com.sridhar.harbor.data.qbit

import com.google.common.truth.Truth.assertThat
import com.sridhar.harbor.data.HarborJson
import org.junit.Test

class TorrentTest {
    private fun t(state: String) = Torrent(hash = "h", state = state)

    @Test fun phaseCoversQbit4AndQbit5StateNames() {
        assertThat(t("downloading").phase).isEqualTo(TorrentPhase.Downloading)
        assertThat(t("metaDL").phase).isEqualTo(TorrentPhase.Downloading)
        assertThat(t("stalledDL").phase).isEqualTo(TorrentPhase.Waiting)
        assertThat(t("uploading").phase).isEqualTo(TorrentPhase.Seeding)
        assertThat(t("pausedDL").phase).isEqualTo(TorrentPhase.Paused)
        assertThat(t("stoppedDL").phase).isEqualTo(TorrentPhase.Paused)
        assertThat(t("stoppedUP").phase).isEqualTo(TorrentPhase.Done)
        assertThat(t("missingFiles").phase).isEqualTo(TorrentPhase.Error)
        assertThat(t("somethingNew").phase).isEqualTo(TorrentPhase.Waiting)
    }

    @Test fun stoppedDetection() {
        assertThat(t("pausedUP").isStopped).isTrue()
        assertThat(t("stoppedDL").isStopped).isTrue()
        assertThat(t("forcedDL").isStopped).isFalse()
    }

    @Test fun labelsAreHumanReadable() {
        assertThat(t("metaDL").stateLabel).isEqualTo("Fetching metadata")
        assertThat(t("stoppedUP").stateLabel).isEqualTo("Completed")
        assertThat(t("stalledUP").stateLabel).isEqualTo("Seeding (idle)")
    }

    @Test fun decodingToleratesUnknownAndMissingFields() {
        val tor = HarborJson.decodeFromString(Torrent.serializer(), """{"hash":"x","name":"Leo","num_seeds":7,"brand_new_field":true}""")
        assertThat(tor.seeds).isEqualTo(7)
        assertThat(tor.progress).isEqualTo(0f)
    }
}
