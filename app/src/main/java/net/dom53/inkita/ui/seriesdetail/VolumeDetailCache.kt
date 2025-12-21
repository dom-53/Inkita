package net.dom53.inkita.ui.seriesdetail

import net.dom53.inkita.data.api.dto.VolumeDto

data class VolumeDetailPayload(
    val seriesId: Int,
    val volume: VolumeDto,
    val formatId: Int? = null,
)

object VolumeDetailCache {
    private val data = mutableMapOf<Int, VolumeDetailPayload>()

    fun put(payload: VolumeDetailPayload) {
        data[payload.volume.id] = payload
    }

    fun get(volumeId: Int): VolumeDetailPayload? = data[volumeId]

    fun remove(volumeId: Int) {
        data.remove(volumeId)
    }
}
