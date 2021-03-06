package koodies.docker

import koodies.builder.buildArray
import koodies.docker.DockerExitStateHandler.Failed
import koodies.exec.RendererProviders
import koodies.exec.parse
import koodies.or
import koodies.text.Semantics.formattedAs
import koodies.tracing.rendering.RendererProvider

/**
 * Search one or more stopped containers.
 */
public open class DockerSearchCommandLine(
    /**
     * Term to search with.
     */
    public val term: String,
    /**
     * Filter output based on stars
     */
    public val stars: Int? = null,//"stars" to it.toString() } then filter
    /**
     * Filter output based on whether image is automated
     */
    public val isAutomated: Boolean? = null,//{ "is-automated" to it.toString() } then filter
    /**
     * Filter output based on whether image is official
     */
    public val isOfficial: Boolean? = null,//{ "is-official" to it.toString() }) then filter
    /**
     * Max number of search results
     */
    public val limit: Int? = 25,
) : DockerCommandLine(
    dockerCommand = "search",
    arguments = buildArray {
        add("--no-trunc")
        add("--format")
        add("{{.Name}}\t{{.Description}}\t{{.StarCount}}\t{{.IsOfficial}}\t{{.IsAutomated}}")
        stars?.also { +"--filter" + "stars=$it" }
        isAutomated?.also { +"--filter" + "is-automated=$it" }
        isOfficial?.also { +"--filter" + "is-official=$it" }
        limit.also { +"--limit" + "$limit" }
        add(term)
    },
    name = "Searching up to ${limit.formattedAs.input} images",
) {

    public companion object {

        /**
         * Searches for at most [limit] Docker images matching [term],
         * having at least the given number of [stars] and being [automated] and/or [official].
         */
        public fun search(
            term: String,
            stars: Int? = null,
            automated: Boolean? = null,
            official: Boolean? = null,
            limit: Int = 100,
            provider: RendererProvider? = null,
        ): List<DockerSearchResult> =
            DockerSearchCommandLine(term, stars, automated, official, limit)
                .exec.logging(renderer = provider ?: RendererProviders.errorsOnly())
                .parse.columns<DockerSearchResult, Failed>(5) { (name, description, starCount, isOfficial, isAutomated) ->
                    DockerSearchResult(DockerImage { name }, description, starCount.toIntOrNull() ?: 0, isOfficial.isNotBlank(), isAutomated.isNotBlank())
                } or { emptyList() }
    }

    public data class DockerSearchResult(
        public val image: DockerImage,
        public val description: String,
        public val stars: Int,
        public val official: Boolean,
        public val automated: Boolean,
    )
}
