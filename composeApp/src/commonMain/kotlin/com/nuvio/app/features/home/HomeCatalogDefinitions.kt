package com.nuvio.app.features.home

import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.supportsPagination
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_catalog_default_title
import org.jetbrains.compose.resources.getString

data class HomeCatalogDefinition(
    val key: String,
    val defaultTitle: String,
    val catalogName: String,
    val addonName: String,
    val manifestUrl: String,
    val type: String,
    val catalogId: String,
    val genre: String? = null,
    val supportsPagination: Boolean,
    val descriptorSignature: String,
) {
    val cacheKey: String
        get() = "$key|$descriptorSignature"

    fun titleFor(showCatalogType: Boolean): String =
        if (showCatalogType) defaultTitle else (if (genre != null) "$catalogName · $genre" else catalogName)
}

fun buildHomeCatalogRefreshSignature(addons: List<ManagedAddon>): List<String> =
    addons.enabledAddons().mapNotNull { addon ->
        val manifest = addon.manifest ?: return@mapNotNull null
        addon to manifest
    }.flatMap { (addon, manifest) ->
        manifest.catalogs.flatMap { catalog ->
            val genreExtra = catalog.extra.firstOrNull { it.name.equals("genre", ignoreCase = true) }
            val baseSignature = buildHomeCatalogDescriptorSignature(addon, manifest, catalog, null)
            val genreSignatures = genreExtra?.options.orEmpty().map { genre ->
                buildHomeCatalogDescriptorSignature(addon, manifest, catalog, genre)
            }
            listOf(baseSignature) + genreSignatures
        }
    }.sorted()

fun buildHomeCatalogDefinitions(addons: List<ManagedAddon>): List<HomeCatalogDefinition> =
    addons.enabledAddons().mapNotNull { addon ->
        val manifest = addon.manifest ?: return@mapNotNull null
        addon to manifest
    }.flatMap { (addon, manifest) ->
        manifest.catalogs
            .flatMap { catalog ->
                val list = mutableListOf<HomeCatalogDefinition>()
                val supportsPagination = catalog.supportsPagination()
                val typeLabel = localizedMediaTypeLabel(catalog.type)

                if (catalog.extra.none { it.isRequired }) {
                    list.add(
                        HomeCatalogDefinition(
                            key = "${manifest.id}:${catalog.type}:${catalog.id}",
                            defaultTitle = runBlocking {
                                getString(
                                    Res.string.home_catalog_default_title,
                                    catalog.name,
                                    typeLabel,
                                )
                            },
                            catalogName = catalog.name,
                            addonName = addon.displayTitle,
                            manifestUrl = addon.manifestUrl,
                            type = catalog.type,
                            catalogId = catalog.id,
                            genre = null,
                            supportsPagination = supportsPagination,
                            descriptorSignature = buildHomeCatalogDescriptorSignature(addon, manifest, catalog, null),
                        )
                    )
                }

                val genreExtra = catalog.extra.firstOrNull { it.name.equals("genre", ignoreCase = true) }
                genreExtra?.options.orEmpty().forEach { genreOption ->
                    val cleanGenre = genreOption.trim()
                    if (cleanGenre.isNotBlank()) {
                        list.add(
                            HomeCatalogDefinition(
                                key = "${manifest.id}:${catalog.type}:${catalog.id}:${cleanGenre.lowercase()}",
                                defaultTitle = "$cleanGenre ($typeLabel)",
                                catalogName = "${catalog.name} · $cleanGenre",
                                addonName = addon.displayTitle,
                                manifestUrl = addon.manifestUrl,
                                type = catalog.type,
                                catalogId = catalog.id,
                                genre = cleanGenre,
                                supportsPagination = supportsPagination,
                                descriptorSignature = buildHomeCatalogDescriptorSignature(addon, manifest, catalog, cleanGenre),
                            )
                        )
                    }
                }
                list
            }
    }.distinctBy(HomeCatalogDefinition::key)

private fun buildHomeCatalogDescriptorSignature(
    addon: ManagedAddon,
    manifest: AddonManifest,
    catalog: AddonCatalog,
    genre: String? = null,
): String =
    buildString {
        append(addon.displayTitle)
        append('|')
        append(addon.enabled)
        append('|')
        append(addon.isRefreshing)
        append('|')
        append(addon.errorMessage.orEmpty())
        append('|')
        append(addon.manifestUrl)
        append('|')
        append(manifest.id)
        append('|')
        append(manifest.name)
        append('|')
        append(manifest.version)
        append('|')
        append(manifest.description)
        append('|')
        append(manifest.logoUrl.orEmpty())
        append('|')
        append(manifest.transportUrl)
        append('|')
        append(manifest.types.joinToString(","))
        append('|')
        append(manifest.idPrefixes.joinToString(","))
        append('|')
        append(manifest.resources.joinToString(",") { resource ->
            listOf(
                resource.name,
                resource.types.joinToString("/"),
                resource.idPrefixes.joinToString("/"),
            ).joinToString(":")
        })
        append('|')
        append(
            listOf(
                manifest.behaviorHints.configurable,
                manifest.behaviorHints.configurationRequired,
                manifest.behaviorHints.adult,
                manifest.behaviorHints.p2p,
            ).joinToString(":"),
        )
        append('|')
        append(catalog.type)
        append('|')
        append(catalog.id)
        append('|')
        append(catalog.name)
        append('|')
        append(catalog.supportsPagination())
        append('|')
        append(genre.orEmpty())
        append('|')
        append(catalog.extra.joinToString(",") { extra ->
            listOf(
                extra.name,
                extra.isRequired.toString(),
                extra.options.joinToString("/"),
                extra.optionsLimit?.toString().orEmpty(),
            ).joinToString(":")
        })
    }

internal fun String.displayLabel(): String = localizedMediaTypeLabel(this)
