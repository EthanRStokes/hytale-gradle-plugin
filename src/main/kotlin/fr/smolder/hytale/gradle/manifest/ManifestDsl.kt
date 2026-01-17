package fr.smolder.hytale.gradle.manifest

import groovy.json.JsonOutput
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.File
import javax.inject.Inject

/**
 * DSL for configuring a pack manifest.
 * 
 * Example usage:
 * ```kotlin
 * hytale {
 *     manifest {
 *         group = "MyOrg"
 *         name = "MyPack"
 *         version = project.version.toString()
 *         description = "An awesome pack!"
 *         
 *         author {
 *             name = "Developer"
 *             email = "dev@example.com"
 *             url = "https://example.com"
 *         }
 *         
 *         website = "https://mypack.com"
 *         serverVersion = "*"
 *         
 *         dependency("SomeRequiredPack", "1.0.0")
 *         optionalDependency("SomeOptionalPack", "*")
 *         
 *         disabledByDefault = false
 *         main = "com.example.MyPlugin"
 *         includesAssetPack = true
 *     }
 * }
 * ```
 */
abstract class ManifestDsl @Inject constructor(
    private val objects: ObjectFactory,
    private val project: Project
) {
    internal abstract val groupProperty: Property<String>
    internal abstract val nameProperty: Property<String>
    internal abstract val versionProperty: Property<String>
    internal abstract val descriptionProperty: Property<String>
    internal abstract val authorsProperty: ListProperty<Author>
    internal abstract val websiteProperty: Property<String>
    internal abstract val serverVersionProperty: Property<String>
    internal abstract val dependenciesProperty: MapProperty<String, String>
    internal abstract val optionalDependenciesProperty: MapProperty<String, String>
    internal abstract val disabledByDefaultProperty: Property<Boolean>
    internal abstract val mainProperty: Property<String>
    internal abstract val includesAssetPackProperty: Property<Boolean>

    /** Your organization or group name (required) */
    var group: String
        get() = groupProperty.orNull ?: ""
        set(value) = groupProperty.set(value)

    /** The name of your Pack (required) */
    var name: String
        get() = nameProperty.orNull ?: ""
        set(value) = nameProperty.set(value)

    /** Version number using semantic versioning (required) */
    var version: String
        get() = versionProperty.orNull ?: project.version.toString()
        set(value) = versionProperty.set(value)

    /** A brief description of what your Pack does (required) */
    var description: String
        get() = descriptionProperty.orNull ?: ""
        set(value) = descriptionProperty.set(value)

    /** Your website or project page (optional) */
    var website: String?
        get() = websiteProperty.orNull
        set(value) = websiteProperty.set(value)

    /** Compatible server version, use "*" for all (required) */
    var serverVersion: String
        get() = serverVersionProperty.orNull ?: "*"
        set(value) = serverVersionProperty.set(value)

    /** Whether Pack loads automatically (optional) */
    var disabledByDefault: Boolean
        get() = disabledByDefaultProperty.orNull ?: false
        set(value) = disabledByDefaultProperty.set(value)

    /** Main class for plugins (optional, plugin-specific) */
    var main: String?
        get() = mainProperty.orNull
        set(value) = mainProperty.set(value)

    /** Whether this pack includes assets (optional, plugin-specific) */
    var includesAssetPack: Boolean
        get() = includesAssetPackProperty.orNull ?: false
        set(value) = includesAssetPackProperty.set(value)

    init {
        // Set sensible defaults
        versionProperty.convention(project.provider { project.version.toString() })
        serverVersionProperty.convention("*")
        disabledByDefaultProperty.convention(false)
        includesAssetPackProperty.convention(false)
    }

    /** Add an author using a configuration block */
    fun author(action: Action<AuthorBuilder>) {
        val builder = AuthorBuilder()
        action.execute(builder)
        authorsProperty.add(builder.build())
    }

    /** Add an author by name only */
    fun author(name: String) {
        authorsProperty.add(Author(name = name))
    }

    /** Add a required dependency */
    fun dependency(packName: String, version: String = "*") {
        dependenciesProperty.put(packName, version)
    }

    /** Add an optional dependency */
    fun optionalDependency(packName: String, version: String = "*") {
        optionalDependenciesProperty.put(packName, version)
    }

    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        // required
        map["Group"] = group
        map["Name"] = name
        map["Version"] = version
        map["Description"] = description
        map["Authors"] = authorsProperty.getOrElse(emptyList()).map { it.toMap() }
        map["ServerVersion"] = serverVersion

        // optional
        website?.let { map["Website"] = it }

        map["Dependencies"] = dependenciesProperty.getOrElse(emptyMap())
        map["OptionalDependencies"] = optionalDependenciesProperty.getOrElse(emptyMap())
        map["DisabledByDefault"] = disabledByDefault

        // plugin-specific
        main?.let { map["Main"] = it }
        if (includesAssetPack) {
            map["IncludesAssetPack"] = includesAssetPack
        }

        return map
    }

    fun toJson(): String {
        return JsonOutput.prettyPrint(JsonOutput.toJson(toMap()))
    }

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(toJson())
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (group.isBlank()) {
            errors.add("'group' is required")
        }
        if (name.isBlank()) {
            errors.add("'name' is required")
        }
        if (version.isBlank()) {
            errors.add("'version' is required")
        }
        if (description.isBlank()) {
            errors.add("'description' is required")
        }
        if (authorsProperty.getOrElse(emptyList()).isEmpty()) {
            errors.add("At least one author is required")
        }
        if (serverVersion.isBlank()) {
            errors.add("'serverVersion' is required")
        }

        return errors
    }

    fun isValid(): Boolean = validate().isEmpty()
}

/**
 * Represents an author of a pack.
 */
data class Author(
    val name: String,
    val email: String? = null,
    val url: String? = null
) {
    fun toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["Name"] = name
        email?.let { map["Email"] = it }
        url?.let { map["Url"] = it }
        return map
    }
}

/**
 * Builder for creating Author instances in the DSL.
 */
class AuthorBuilder {
    var name: String = ""
    var email: String? = null
    var url: String? = null

    fun build(): Author {
        require(name.isNotBlank()) { "Author name cannot be blank" }
        return Author(name = name, email = email, url = url)
    }
}
