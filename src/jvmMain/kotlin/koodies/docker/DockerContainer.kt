package koodies.docker

import koodies.asString
import koodies.builder.StatelessBuilder
import koodies.docker.DockerContainer.Companion.ContainerContext
import koodies.docker.DockerContainer.State.Error
import koodies.docker.DockerContainer.State.Existent.Created
import koodies.docker.DockerContainer.State.Existent.Dead
import koodies.docker.DockerContainer.State.Existent.Exited
import koodies.docker.DockerContainer.State.Existent.Paused
import koodies.docker.DockerContainer.State.Existent.Removing
import koodies.docker.DockerContainer.State.Existent.Restarting
import koodies.docker.DockerContainer.State.Existent.Running
import koodies.docker.DockerContainer.State.NotExistent
import koodies.docker.DockerExitStateHandler.Failed
import koodies.exec.Process.ExitState
import koodies.exec.RendererProviders.noDetails
import koodies.exec.parse
import koodies.io.path.pathString
import koodies.lowerSentenceCaseName
import koodies.map
import koodies.or
import koodies.regex.get
import koodies.requireSaneInput
import koodies.text.CharRanges.Alphanumeric
import koodies.text.Semantics.Symbols
import koodies.text.Semantics.formattedAs
import koodies.text.capitalize
import koodies.text.randomString
import koodies.text.truncate
import koodies.text.withRandomSuffix
import koodies.text.wrap
import koodies.time.seconds
import koodies.tracing.rendering.ReturnValue
import java.nio.file.Path
import kotlin.time.Duration

public class DockerContainer(public val name: String) {

    init {
        name.requireSaneInput()
        require(isValid(name)) { "${name.formattedAs.input} is invalid. It needs to match ${REGEX.formattedAs.input}." }
    }

    /**
     * Current state of this container.
     */
    public val containerState: State get() = queryState(this).also { cachedState = it }

    /**
     * Last known state of this container.
     */
    public var cachedState: State? = null
        private set

    public val exists: Boolean get() = containerState !is NotExistent
    public val isCreated: Boolean get() = containerState is Created
    public val isRestarting: Boolean get() = containerState is Restarting
    public val isRunning: Boolean get() = containerState is Running
    public val isRemoving: Boolean get() = containerState is Removing
    public val isPaused: Boolean get() = containerState is Paused
    public val isExited: Boolean get() = containerState is Exited
    public val isDead: Boolean get() = containerState is Dead

    public sealed class State(override val successful: Boolean, override val symbol: String) : ReturnValue {

        public object NotExistent : State(false, Symbols.Negative)

        public sealed class Existent(successful: Boolean, symbol: String, public val status: String) : State(successful, symbol) {
            public class Created(status: String) : Existent(true, "✱", status)
            public class Restarting(status: String) : Existent(true, "↻", status)
            public class Running(status: String) : Existent(true, "▶", status)
            public class Removing(status: String) : Existent(true, "♻︎", status)
            public class Paused(status: String) : Existent(true, "❚❚", status)
            public class Exited(status: String, public val exitCode: Int? = parseExitCode(status)) :
                Existent(true, if (exitCode == 0) Symbols.OK else Symbols.Error + " " + exitCode.formattedAs.error, status) {
                override val successful: Boolean get() = exitCode == 0

                private companion object {
                    private val exitCodeRegex = Regex(".*\\((?<exitCode>\\d+)\\).*")
                    private fun parseExitCode(status: String): Int? =
                        exitCodeRegex.matchEntire(status)?.get("exitCode")?.toInt()
                }
            }

            public class Dead(status: String) : Existent(false, "✝", status)

            override fun toString(): String = format()
        }

        public data class Error(val code: Int, val message: String) : State(false, Symbols.Error)

        override val textRepresentation: String = this::class.lowerSentenceCaseName
        override fun toString(): String = textRepresentation
    }

    /**
     * Starts this container.
     *
     * @param attach whether to attach STDOUT/STDERR and forward signals
     * @param interactive whether to attach this container's STDIN
     */
    public fun start(
        attach: Boolean = true,
        interactive: Boolean = false,
    ): ExitState =
        DockerStartCommandLine(name, attach = attach, interactive = interactive)
            .exec.logging(renderer = noDetails()).waitFor()

    /**
     * Stops this container with the optionally specified [timeout] (default: 5 seconds).
     */
    public fun stop(
        timeout: Duration? = 5.seconds,
        async: Boolean = false,
    ): ExitState =
        DockerStopCommandLine(name, time = timeout)
            .exec.apply { if (async) mode { this.async } }
            .logging(renderer = noDetails()).waitFor()

    /**
     * Kills this container with the optionally specified [signal] (default: KILL).
     */
    public fun kill(
        signal: String? = null,
        async: Boolean = false,
    ): ExitState =
        DockerKillCommandLine(name, signal = signal)
            .exec.apply { if (async) mode { this.async } }
            .logging(renderer = noDetails()).waitFor()

    /**
     * Removes this container.
     *
     * @param force if the container is running, kill it before removing it
     * @param link remove the specified link associated with the container
     * @param volumes remove anonymous volumes associated with the container
     */
    public fun remove(
        force: Boolean = false,
        link: Boolean = false,
        volumes: Boolean = false,
    ): ExitState =
        DockerRemoveCommandLine(name, force = force, link = link, volumes = volumes)
            .exec.logging(renderer = noDetails()).waitFor()

    override fun toString(): String = asString {
        ::name.name to name
        "state" to cachedState
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DockerContainer

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int = name.hashCode()

    public companion object : StatelessBuilder.Returning<ContainerContext, DockerContainer>(ContainerContext) {

        private fun queryState(container: DockerContainer): State =
            DockerPsCommandLine(true, container.name)
                .exec()
                .parse.columns<State, Failed>(3) { (_, state, status) ->
                    when (state.capitalize()) {
                        Created::class.simpleName -> Created(status)
                        Restarting::class.simpleName -> Restarting(status)
                        Running::class.simpleName -> Running(status)
                        Removing::class.simpleName -> Removing(status)
                        Paused::class.simpleName -> Paused(status)
                        Exited::class.simpleName -> Exited(status)
                        Dead::class.simpleName -> Dead(status)
                        else -> Error(-1, "Unknown status $state: $status")
                    }
                }.map { singleOrNull() ?: NotExistent } or { error(it) }

        /**
         * Lists locally available instances this containers.
         */
        public fun list(): List<DockerContainer> =
            DockerPsCommandLine(true)
                .exec.logging(renderer = noDetails())
                .parse.columns<DockerContainer, Failed>(3) { (name, _, _) ->
                    DockerContainer(name)
                } or { error(it) }

        /**
         * Starts the given [containers].
         *
         * @param attach whether to attach STDOUT/STDERR and forward signals
         * @param interactive whether to attach this container's STDIN
         */
        public fun start(
            vararg containers: DockerContainer,
            attach: Boolean = true,
            interactive: Boolean = false,
        ): ExitState =
            DockerStartCommandLine(*containers.map { it.name }.toTypedArray(), attach = attach, interactive = interactive)
                .exec.logging(renderer = noDetails()).waitFor()


        /**
         * Stops the given [containers] with the optionally specified [timeout] (default: 5 seconds).
         */
        public fun stop(
            vararg containers: DockerContainer,
            timeout: Duration = 5.seconds,
        ): ExitState =
            DockerStopCommandLine(*containers.map { it.name }.toTypedArray(), time = timeout)
                .exec.logging(renderer = noDetails()).waitFor()

        /**
         * Kills the given [containers] with the optionally specified [signal] (default: KILL).
         */
        public fun kill(
            vararg containers: DockerContainer,
            signal: String? = null,
            async: Boolean = false,
        ): ExitState =
            DockerKillCommandLine(*containers.map { it.name }.toTypedArray(), signal = signal)
                .exec.apply { if (async) mode { this.async } }
                .logging(renderer = noDetails()).waitFor()

        /**
         * Removes the given [containers].
         *
         * @param force    if the container is running, kill it before removing it
         * @param link remove the specified link associated with the container
         * @param volumes remove anonymous volumes associated with the container
         */
        public fun remove(
            vararg containers: DockerContainer,
            force: Boolean = false,
            link: Boolean = false,
            volumes: Boolean = false,
        ): ExitState =
            DockerRemoveCommandLine(*containers.map { it.name }.toTypedArray(), force = force, link = link, volumes = volumes)
                .exec.logging(renderer = noDetails()).waitFor()

        /**
         * Builder to provide DSL elements to create instances of [DockerImage].
         */

        public object ContainerContext {

            /**
             * Sanitizes this character sequence and returns it as a Docker container.
             */
            public val <T : CharSequence> T.sanitized: DockerContainer get() = from(this.toString(), randomSuffix = false)

            /**
             * Sanitizes and appends a random suffix to this character sequence before
             * returning the result as a Docker container.
             */
            public val <T : CharSequence> T.withRandomSuffix: DockerContainer get() = from(this.toString(), randomSuffix = true)

            /**
             * Sanitizes the [Path.getFileName] of this [Path] and returns it as a Docker container.
             */
            public val <T : Path> T.sanitized: DockerContainer get() = from(this, randomSuffix = false)

            /**
             * Sanitizes and appends a random suffix to the [Path.getFileName] of this [Path] before
             * returning the result as a Docker container.
             */
            public val <T : Path> T.withRandomSuffix: DockerContainer get() = from(this, randomSuffix = true)

            /**
             * Appends a random suffix to this docker container (name) and returns it.
             */
            public val DockerContainer.withRandomSuffix: DockerContainer get() = from(name, randomSuffix = true)
        }

        /**
         * Valid length of a Docker container (name).
         */
        private val LENGTH_RANGE: IntRange = 1..128


        /**
         * Pattern that matches a valid Docker container (name).
         */
        public val REGEX: Regex = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]" + "${LENGTH_RANGE.first - 1},${LENGTH_RANGE.last}".wrap("{", "}"))

        private fun isValid(name: String) = name.length in LENGTH_RANGE && name.matches(REGEX)


        /**
         * Checks if the given [name] and the given [suffix] form a valid [DockerContainer]
         * name and if not transforms it to a valid one.
         */
        private fun sanitize(name: String, suffix: String = ""): String {
            val nameWithSuffix = name.truncate((LENGTH_RANGE.last - suffix.length).coerceAtLeast(0), "...") + suffix
            if (isValid(nameWithSuffix)) return nameWithSuffix
            var replaceWithXToGuaranteeAValidName = true
            return nameWithSuffix.map { c ->
                val isAlphaNumeric = Alphanumeric.contains(c)
                if (isAlphaNumeric) replaceWithXToGuaranteeAValidName = false
                if (replaceWithXToGuaranteeAValidName) return@map "X"
                when {
                    isAlphaNumeric -> c
                    "._-".contains(c) -> c
                    c.isWhitespace() -> "-"
                    else -> '_'
                }
            }.joinToString("",
                postfix = (LENGTH_RANGE.first - nameWithSuffix.length)
                    .takeIf { it > 0 }?.let {
                        randomString(it, Alphanumeric)
                    } ?: "")
                .let {
                    it.take(it.length.coerceAtMost(LENGTH_RANGE.last - suffix.length)) + suffix
                }
                .also { check(isValid(it)) }
        }

        /**
         * Transforms the given [name] to a valid [DockerContainer] name
         * and adds a [randomSuffix] to it by default.
         */
        public fun from(name: String, randomSuffix: Boolean = false): DockerContainer =
            DockerContainer(sanitize(name, if (randomSuffix) "".withRandomSuffix() else ""))

        /**
         * Transforms the given [Path] to a valid [DockerContainer] name
         * and adds a [randomSuffix] to it by default.
         */
        public fun from(path: Path, randomSuffix: Boolean = true): DockerContainer =
            from(path.fileName.pathString, randomSuffix)
    }
}
