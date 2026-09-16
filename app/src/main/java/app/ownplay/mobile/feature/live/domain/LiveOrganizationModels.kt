package app.ownplay.mobile.feature.live.domain

import kotlinx.coroutines.flow.Flow

enum class LiveOrganizationMode {
    PROVIDER,
    OWNPLAY,
}

enum class LiveOrganizationOrigin {
    PROVIDER,
    AUTO,
    MANUAL,
}

enum class LiveClassificationConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

data class LiveCategoryScope(
    val sourceId: String,
    val mode: LiveOrganizationMode,
    val parentCategoryId: String? = null,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
    }
}

data class LiveChannelMembershipScope(
    val sourceId: String,
    val mode: LiveOrganizationMode,
    val categoryId: String,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(categoryId.isNotBlank()) { "categoryId must not be blank" }
    }
}

data class LiveOrganizationCategory(
    val sourceId: String,
    val mode: LiveOrganizationMode,
    val categoryId: String,
    val parentCategoryId: String? = null,
    val displayName: String,
    val semanticKey: String? = null,
    val origin: LiveOrganizationOrigin,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(categoryId.isNotBlank()) { "categoryId must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(LiveOrganizationScopePolicy.originMatchesMode(mode, origin)) {
            "origin does not match organization mode"
        }
    }

    val scope: LiveCategoryScope
        get() = LiveCategoryScope(sourceId, mode, parentCategoryId)
}

data class LiveCategoryPersonalizationKey(
    val sourceId: String,
    val mode: LiveOrganizationMode,
    val categoryId: String,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(categoryId.isNotBlank()) { "categoryId must not be blank" }
    }
}

data class LiveChannelMembership(
    val sourceId: String,
    val mode: LiveOrganizationMode,
    val categoryId: String,
    val channelId: String,
    val included: Boolean = true,
    val origin: LiveOrganizationOrigin,
    val confidence: LiveClassificationConfidence? = null,
    val evidenceKeys: Set<String> = emptySet(),
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(categoryId.isNotBlank()) { "categoryId must not be blank" }
        require(channelId.isNotBlank()) { "channelId must not be blank" }
        require(LiveOrganizationScopePolicy.originMatchesMode(mode, origin)) {
            "origin does not match organization mode"
        }
    }

    val scope: LiveChannelMembershipScope
        get() = LiveChannelMembershipScope(sourceId, mode, categoryId)
}

data class LiveChannelMembershipPersonalizationKey(
    val sourceId: String,
    val mode: LiveOrganizationMode,
    val categoryId: String,
    val channelId: String,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(categoryId.isNotBlank()) { "categoryId must not be blank" }
        require(channelId.isNotBlank()) { "channelId must not be blank" }
    }
}

data class LiveOrganizationSnapshot(
    val sourceId: String,
    val activeMode: LiveOrganizationMode = LiveOrganizationMode.PROVIDER,
    val categories: List<LiveOrganizationCategory> = emptyList(),
    val memberships: List<LiveChannelMembership> = emptyList(),
)

object LiveOrganizationScopePolicy {
    const val PROVIDER_UNCATEGORIZED_CATEGORY_ID = "__provider_uncategorized__"

    fun providerCategoryId(providerCategoryKey: String?): String =
        providerCategoryKey?.trim()?.takeIf(String::isNotEmpty)
            ?: PROVIDER_UNCATEGORIZED_CATEGORY_ID

    fun originMatchesMode(
        mode: LiveOrganizationMode,
        origin: LiveOrganizationOrigin,
    ): Boolean = when (mode) {
        LiveOrganizationMode.PROVIDER -> origin == LiveOrganizationOrigin.PROVIDER
        LiveOrganizationMode.OWNPLAY -> origin != LiveOrganizationOrigin.PROVIDER
    }

    fun canReorderCategories(
        scope: LiveCategoryScope,
        categories: List<LiveOrganizationCategory>,
    ): Boolean = categories.isNotEmpty() &&
        categories.all { it.scope == scope } &&
        categories.map { it.categoryId }.distinct().size == categories.size

    fun canReorderChannels(
        scope: LiveChannelMembershipScope,
        memberships: List<LiveChannelMembership>,
    ): Boolean = memberships.isNotEmpty() &&
        memberships.all { it.scope == scope && it.included } &&
        memberships.map { it.channelId }.distinct().size == memberships.size
}

interface LiveOrganizationRepository {
    fun observeOrganization(sourceId: String): Flow<LiveOrganizationSnapshot>

    suspend fun setActiveMode(sourceId: String, mode: LiveOrganizationMode)

    suspend fun setCategoryHidden(key: LiveCategoryPersonalizationKey, hidden: Boolean)

    suspend fun setCategoryOrder(scope: LiveCategoryScope, orderedCategoryIds: List<String>)

    suspend fun resetCategoryOrder(scope: LiveCategoryScope)

    suspend fun setChannelHidden(key: LiveChannelMembershipPersonalizationKey, hidden: Boolean)

    suspend fun setChannelOrder(scope: LiveChannelMembershipScope, orderedChannelIds: List<String>)

    suspend fun resetChannelOrder(scope: LiveChannelMembershipScope)
}
