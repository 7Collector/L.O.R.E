package collector.freya.app.odin.models

import android.net.Uri

enum class AttachmentType {
    IMAGE, AUDIO, FILE
}

data class Attachment(val type: AttachmentType, val uri: Uri, val name: String)