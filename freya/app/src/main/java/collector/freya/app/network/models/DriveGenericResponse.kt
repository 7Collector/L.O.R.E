package collector.freya.app.network.models

import kotlinx.serialization.Serializable

@Serializable
data class DriveGenericResponse(
    val status: String,
    val createdPath: String? = null,
    val deletedPath: String? = null,
    val oldPath: String? = null,
    val newPath: String? = null,
)

@Serializable
data class CreateSharePayload(
    val resource_type: String,
    val resource_id: Int,
    val permission: String = "view",
    val expires_in: Double? = null,
    val requires_email: Boolean = false,
    val allowed_emails: List<String>? = null,
    val password: String? = null,
    val max_uses: Int? = null,
)

@Serializable
data class CreateShareResponse(
    val share_id: Int,
    val share_link_id: Int,
    val token: String,
    val share_url: String,
)

@Serializable
data class ShareItem(
    val share_id: Int,
    val resource_type: String,
    val resource_id: Int,
    val permission: String,
    val expires_at: Double? = null,
    val revoked: Boolean,
    val link_id: Int,
    val requires_email: Boolean,
    val allowed_emails: List<String>? = null,
    val max_uses: Int? = null,
    val use_count: Int,
)

@Serializable
data class ShareListResponse(
    val shares: List<ShareItem>
)

@Serializable
data class QuotaUsageResponse(
    val total_usage_bytes: Long
)