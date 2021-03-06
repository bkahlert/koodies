package koodies.docker

import koodies.builder.buildArray
import koodies.text.Semantics.FieldDelimiters
import koodies.text.Semantics.formattedAs
import koodies.text.spaced

/**
 * [DockerImageCommandLine] that removes the specified [images].
 */
public open class DockerImageRemoveCommandLine(
    /**
     * Images to be removed.
     */
    public vararg val images: DockerImage,
    /**
     * Force removal of the image
     */
    public val force: Boolean = false,
) : DockerImageCommandLine(
    dockerImageCommand = "rm",
    dockerImageArguments = buildArray {
        if (force) add("--force")
        images.forEach { add(it.toString()) }
    },
    name = kotlin.run {
        val forcefully = if (force) " forcefully".formattedAs.warning else ""
        "Removing$forcefully ${images.joinToString(FieldDelimiters.FIELD.spaced) { it.formattedAs.input }}"
    },
)
