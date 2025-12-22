package net.dom53.inkita.ui.seriesdetail

import net.dom53.inkita.data.api.dto.VolumeDto
import net.dom53.inkita.core.logging.LoggingManager

data class VolumeDetailPayload(
    val seriesId: Int,
    val libraryId: Int? = null,
    val volume: VolumeDto,
    val formatId: Int? = null,
)

object VolumeDetailCache {
    private val data = mutableMapOf<Int, VolumeDetailPayload>()

    fun put(payload: VolumeDetailPayload) {
        data[payload.volume.id] = payload
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "VolumeDetailCache",
                "Put volume=${payload.volume.id} series=${payload.seriesId}",
            )
        }
    }

    fun get(volumeId: Int): VolumeDetailPayload? {
        val payload = data[volumeId]
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "VolumeDetailCache",
                "Get volume=$volumeId hit=${payload != null}",
            )
        }
        return payload
    }

    fun remove(volumeId: Int) {
        data.remove(volumeId)
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d("VolumeDetailCache", "Remove volume=$volumeId")
        }
    }
}
