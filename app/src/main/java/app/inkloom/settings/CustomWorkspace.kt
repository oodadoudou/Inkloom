package app.inkloom.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.InputStream

data class CustomWorkspaceProfile(
    val id: String,
    val name: String,
    val layouts: WorkspaceLayoutProfiles,
)

object CustomWorkspaces {
    const val MAX_COUNT = 24
    const val MAX_NAME_CHARS = 60

    fun normalize(values: List<CustomWorkspaceProfile>): List<CustomWorkspaceProfile> {
        val seenIds = mutableSetOf<String>()
        return values.mapNotNull { value ->
            val id = value.id.trim().take(80)
            val name = normalizeName(value.name)
            val layouts = complete(value.layouts)
            if (id.isBlank() || name.isBlank() || !seenIds.add(id)) null else value.copy(id = id, name = name, layouts = layouts)
        }.take(MAX_COUNT)
    }

    fun capture(id: String, name: String, settings: AppSettings, orientation: WorkspaceOrientation): CustomWorkspaceProfile {
        val recorded = settings.recordWorkspaceFor(orientation)
        return CustomWorkspaceProfile(id.trim().take(80), normalizeName(name), complete(recorded.workspaceLayouts))
    }

    fun apply(settings: AppSettings, profile: CustomWorkspaceProfile, orientation: WorkspaceOrientation): AppSettings {
        val normalized = normalize(listOf(profile)).firstOrNull() ?: return settings
        return settings.copy(workspaceLayouts = normalized.layouts).withWorkspaceFor(orientation)
    }

    fun uniqueName(requested: String, existing: List<CustomWorkspaceProfile>): String {
        val base = normalizeName(requested).ifBlank { "Workspace" }
        val used = existing.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in used) return base
        var suffix = 2
        while (true) {
            val suffixText = " $suffix"
            val candidate = base.take((MAX_NAME_CHARS - suffixText.length).coerceAtLeast(1)) + suffixText
            if (candidate.lowercase() !in used) return candidate
            suffix++
        }
    }

    fun normalizeName(value: String): String = value
        .replace(Regex("[\\p{Cc}\\p{Cf}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_NAME_CHARS)

    private fun complete(layouts: WorkspaceLayoutProfiles): WorkspaceLayoutProfiles {
        val fallback = AppSettings().captureToolboxWorkspace()
        return WorkspaceLayoutProfiles(
            landscape = (layouts.landscape ?: fallback).normalizedWorkspace(),
            portrait = (layouts.portrait ?: fallback).normalizedWorkspace(),
        )
    }
}

object CustomWorkspaceCodec {
    const val MIME_TYPE = "application/vnd.inkloom.workspace+json"
    const val FILE_EXTENSION = "inkloom-workspace"
    const val MAX_FILE_BYTES = 256 * 1024
    private const val PROFILE_FORMAT = "inkloom-workspace"
    private const val LIBRARY_FORMAT = "inkloom-workspace-library"
    private const val VERSION = 1
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun encodeProfile(profile: CustomWorkspaceProfile): ByteArray {
        val normalized = CustomWorkspaces.normalize(listOf(profile)).singleOrNull()
            ?: error("Workspace profile is invalid.")
        return json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("format", PROFILE_FORMAT)
            put("version", VERSION)
            put("workspace", normalized.toJson())
        }).encodeToByteArray().also { require(it.size <= MAX_FILE_BYTES) { "Workspace profile is too large." } }
    }

    fun decodeProfile(bytes: ByteArray): Result<CustomWorkspaceProfile> = runCatching {
        require(bytes.isNotEmpty() && bytes.size <= MAX_FILE_BYTES) { "Workspace profile has an invalid size." }
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(root.text("format") == PROFILE_FORMAT && root.int("version", 0) == VERSION) { "Unsupported workspace profile." }
        root.getValue("workspace").jsonObject.toProfile().also {
            require(it.layouts.landscape != null && it.layouts.portrait != null) { "Workspace profile has no usable layouts." }
        }
    }

    fun decodeProfile(input: InputStream): Result<CustomWorkspaceProfile> = runCatching {
        decodeProfile(input.readLimited()).getOrThrow()
    }

    fun encodeLibrary(values: List<CustomWorkspaceProfile>): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", LIBRARY_FORMAT)
            put("version", VERSION)
            put("workspaces", buildJsonArray { CustomWorkspaces.normalize(values).forEach { add(it.toJson()) } })
        },
    )

    fun decodeLibrary(raw: String?): List<CustomWorkspaceProfile> {
        if (raw.isNullOrBlank() || raw.length > MAX_FILE_BYTES) return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            require(root.text("format") == LIBRARY_FORMAT && root.int("version", 0) == VERSION)
            CustomWorkspaces.normalize(root["workspaces"]?.jsonArray.orEmpty().map { it.jsonObject.toProfile() })
        }.getOrDefault(emptyList())
    }

    fun libraryJson(values: List<CustomWorkspaceProfile>): JsonObject =
        json.parseToJsonElement(encodeLibrary(values)).jsonObject

    private fun CustomWorkspaceProfile.toJson() = buildJsonObject {
        put("id", id)
        put("name", name)
        put("layouts", json.parseToJsonElement(WorkspaceLayoutProfilesCodec.encode(layouts)))
    }

    private fun JsonObject.toProfile(): CustomWorkspaceProfile {
        val decodedLayouts = WorkspaceLayoutProfilesCodec.decode(get("layouts")?.toString())
        require(!decodedLayouts.isEmpty) { "Workspace profile has no usable layouts." }
        val profile = CustomWorkspaceProfile(
            id = text("id"),
            name = text("name"),
            layouts = decodedLayouts,
        )
        return CustomWorkspaces.normalize(listOf(profile)).singleOrNull() ?: error("Workspace profile is invalid.")
    }

    private fun InputStream.readLimited(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_FILE_BYTES) { "Workspace profile is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun JsonObject.text(key: String): String = get(key)?.jsonPrimitive?.content.orEmpty()
    private fun JsonObject.int(key: String, fallback: Int): Int = runCatching { get(key)?.jsonPrimitive?.int }.getOrNull() ?: fallback
    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
}
