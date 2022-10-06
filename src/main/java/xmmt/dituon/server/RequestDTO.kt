package xmmt.dituon.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class RequestDTO(
    val key: String,
    val form: TargetDTO = TargetDTO("form", ""),
    val to: TargetDTO = TargetDTO("to", ""),
    val group: TargetDTO = TargetDTO("group", ""),
    val bot: TargetDTO = TargetDTO("bot", ""),
    val textList: List<String> = emptyList()
) {
    companion object {
        @JvmStatic
        fun decodeFromString(json: String): RequestDTO {
            return Json.decodeFromString(json)
        }
    }
}

@Serializable
data class TargetDTO(
    val name: String,
    val avatar: String
)
