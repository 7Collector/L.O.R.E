package collector.freya.app.odin.models

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
enum class AttachmentType {
    IMAGE, AUDIO, FILE
}

@Serializable
data class Attachment(val type: AttachmentType, val uri: String, val name: String)