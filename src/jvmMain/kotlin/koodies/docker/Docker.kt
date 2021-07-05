package koodies.docker

import koodies.docker.Docker.info.get
import koodies.docker.DockerExitStateHandler.Failed
import koodies.docker.DockerSearchCommandLine.DockerSearchResult
import koodies.exec.CommandLine
import koodies.exec.Exec
import koodies.exec.Executable
import koodies.exec.IO
import koodies.exec.ProcessingMode.Interactivity.Interactive
import koodies.exec.ProcessingMode.Interactivity.NonInteractive
import koodies.exec.RendererProviders.noDetails
import koodies.exec.parse
import koodies.exec.successful
import koodies.io.path.deleteRecursively
import koodies.io.path.listDirectoryEntriesRecursively
import koodies.io.path.moveTo
import koodies.io.path.pathString
import koodies.io.randomDirectory
import koodies.map
import koodies.or
import koodies.regex.RegularExpressions
import koodies.shell.ShellScript
import koodies.shell.ShellScript.ScriptContext
import koodies.text.joinToKebabCase
import koodies.text.withRandomSuffix
import koodies.tracing.rendering.RendererProvider
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import java.util.Locale

/**
 * Entrypoint to ease discovery of Docker related features.
 */
public object Docker {

    /**
     * System wide information regarding the Docker installation.
     *
     * Usage:
     * - `info["server.server-version"]`
     * - `info["server", "server-version"]`
     *
     * @see get
     */
    @Suppress("ClassName") // used like an array
    public object info {

        /**
         * Returns the information identified by the given [keys].
         *
         * The [keys] can either be an array of keys,
         * or a single string consisting of `.` separated keys.
         *
         * Each key is expected to be in `kebab-case`.
         *
         * Examples:
         * - `info["server.server-version"]`
         * - `info["server", "server-version"]`
         */
        public operator fun get(vararg keys: String): String? =
            with(keys.flatMap { it.split(".") }.map { it.unify() }.toMutableList()) {
                DockerInfoCommandLine(query = this)
                    .exec.logging(renderer = noDetails())
                    .parse.columns<String, Failed>(1) { (line) ->
                        if (isNotEmpty() && line.substringBefore(":").unify() == first()) {
                            removeAt(0)
                            if (isEmpty()) line.substringAfter(":").trim()
                            else null
                        } else {
                            null
                        }
                    }.map { singleOrNull() } or { null }
            }

        private fun String.unify() = lowercase(Locale.getDefault()).split(RegularExpressions.SPACES).filterNot { it.isEmpty() }.joinToKebabCase()
    }

    /**
     * Entry point for [DockerImage] related features like pulling images.
     */
    public val images: DockerImage.Companion = DockerImage.Companion

    /**
     * Entry point for [DockerContainer] related features like starting containers.
     */
    public val containers: DockerContainer.Companion = DockerContainer.Companion

    /**
     * Whether the Docker engine itself is running.
     */
    public val engineRunning: Boolean get() = DockerInfoCommandLine().exec.invoke().successful

    /**
     * Returns a [DockerContainer] representing a Docker container of the same
     * (sanitized) name.
     */
    public operator fun invoke(name: String): DockerContainer = DockerContainer.from(name, randomSuffix = false)

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
        renderer: RendererProvider? = null,
    ): List<DockerSearchResult> = DockerSearchCommandLine.search(term, stars, automated, official, limit, renderer)

    /**
     * Executes the given [command] and its [arguments] in
     * a [DockerContainer] with the given [image].
     *
     * The given [workingDirectory] is mapped to `/work`,
     * which is configured as the working directory
     * inside of the container.
     *
     * If [inputStream] is set, the contents will be piped to the standard input
     * of the created process.
     */
    public fun exec(
        image: DockerImage,
        workingDirectory: Path,
        command: Any? = null,
        vararg arguments: Any,
        renderer: RendererProvider? = null,
        inputStream: InputStream? = null,
    ): DockerExec = execute(image, workingDirectory, renderer, inputStream) { CommandLine(command?.toString() ?: "", arguments.map { it.toString() }) }

    /**
     * Builds a shell script using the given [scriptInit] and executes it in
     * a [DockerContainer] with the given [image].
     *
     * The given [workingDirectory] is mapped to `/work`,
     * which is configured as the working directory
     * inside of the container and also passed to [scriptInit] as the only argument.
     *
     * If [inputStream] is set, the contents will be piped to the standard input
     * of the created process.
     */
    public fun exec(
        image: DockerImage,
        workingDirectory: Path,
        renderer: RendererProvider? = null,
        inputStream: InputStream? = null,
        scriptInit: ScriptInitWithWorkingDirectory,
    ): DockerExec = execute(image, workingDirectory, renderer, inputStream) { workDir -> ShellScript { scriptInit(workDir) } }

    /**
     * Builds an [Executable] using the given [executableProvider] and executes it in
     * a [DockerContainer] with the given [image].
     *
     * The given [workingDirectory] is mapped to `/work`,
     * which is configured as the working directory
     * inside of the container and also passed to [executableProvider].
     *
     * If [inputStream] is set, the contents will be piped to the standard input
     * of the created process.
     */
    private fun execute(
        image: DockerImage,
        workingDirectory: Path,
        renderer: RendererProvider? = null,
        inputStream: InputStream? = null,
        executableProvider: (ContainerPath) -> Executable<Exec>,
    ): DockerExec {
        val containerPath = "/work".asContainerPath()
        return executableProvider(containerPath).dockerized(image) {
            mounts { workingDirectory mountAt containerPath }
            workingDirectory { containerPath }
        }.run {
            val interactivity = if (inputStream != null) NonInteractive(inputStream) else Interactive(false)
            if (renderer != null) exec.mode { sync(interactivity) }.logging(renderer = renderer)
            else exec.mode { sync(interactivity) }()
        }
    }
}

/**
 * Type of the argument supported by [docker] and its variants (e.g. [ubuntu].
 */
public typealias ScriptInitWithWorkingDirectory = ScriptContext.(ContainerPath) -> CharSequence

/**
 * Runs the given [command] and its [arguments] in
 * a [DockerContainer] with the [DockerImage] parsed from [image].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.docker(
    image: String,
    command: Any? = null,
    vararg arguments: Any,
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
): DockerExec =
    Docker.exec(DockerImage { image }, this, command, *arguments, renderer = renderer, inputStream = inputStream)

/**
 * Runs the given [command] and its [arguments] in
 * a [DockerContainer] with the [DockerImage] built using [imageInit].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.docker(
    imageInit: DockerImageInit,
    command: Any? = null,
    vararg arguments: Any,
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
): DockerExec =
    Docker.exec(DockerImage(imageInit), this, command, *arguments, renderer = renderer, inputStream = inputStream)

/**
 * Runs the given [command] and its [arguments] in
 * a [DockerContainer] with the given [image].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.docker(
    image: DockerImage,
    command: Any? = null,
    vararg arguments: Any,
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
): DockerExec =
    Docker.exec(image, this, command, *arguments, renderer = renderer, inputStream = inputStream)

/**
 * Builds a shell script using the given [scriptInit] and runs it in
 * a [DockerContainer] with the [DockerImage] parsed from [image].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container and also passed to [scriptInit] as the only argument.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.docker(
    image: String,
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
    scriptInit: ScriptInitWithWorkingDirectory,
): DockerExec =
    Docker.exec(DockerImage { image }, this, renderer, inputStream, scriptInit)

/**
 * Builds a shell script using the given [scriptInit] and runs it in
 * a [DockerContainer] with the [DockerImage] built using [imageInit].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container and also passed to [scriptInit] as the only argument.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.docker(
    imageInit: DockerImageInit,
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
    scriptInit: ScriptInitWithWorkingDirectory,
): DockerExec =
    Docker.exec(DockerImage(imageInit), this, renderer, inputStream, scriptInit)

/**
 * Builds a shell script using the given [scriptInit] and runs it in
 * a [DockerContainer] with the given [image].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container and also passed to [scriptInit] as the only argument.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.docker(
    image: DockerImage,
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
    scriptInit: ScriptInitWithWorkingDirectory,
): DockerExec =
    Docker.exec(image, this, renderer, inputStream, scriptInit)


/*
 * UBUNTU
 */

/**
 * Runs the given [command] and its [arguments] in
 * a [Ubuntu](https://hub.docker.com/_/ubuntu) based [DockerContainer].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.ubuntu(
    command: Any? = null,
    vararg arguments: Any,
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
): DockerExec =
    docker(DockerImage { "ubuntu" }, command, *arguments, renderer = renderer, inputStream = inputStream)

/**
 * Builds a shell script using the given [scriptInit] and runs it in
 * a [Ubuntu](https://hub.docker.com/_/ubuntu) based [DockerContainer].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container and also passed to [scriptInit] as the only argument.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.ubuntu(
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
    scriptInit: ScriptInitWithWorkingDirectory,
): DockerExec =
    docker(DockerImage { "ubuntu" }, renderer, inputStream, scriptInit)


/*
 * BUSYBOX
 */

/**
 * Runs the given [command] and its [arguments] in
 * a [busybox](https://hub.docker.com/_/busybox) based [DockerContainer].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.busybox(
    command: Any? = null,
    vararg arguments: Any,
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
): DockerExec =
    docker(DockerImage { "busybox" }, command, *arguments, renderer = renderer, inputStream = inputStream)

/**
 * Builds a shell script using the given [scriptInit] and runs it in
 * a [busybox](https://hub.docker.com/_/busybox) based [DockerContainer].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container and also passed to [scriptInit] as the only argument.
 *
 * If [inputStream] is set, the contents will be piped to the standard input
 * of the created process.
 */
public fun Path.busybox(
    renderer: RendererProvider? = null,
    inputStream: InputStream? = null,
    scriptInit: ScriptInitWithWorkingDirectory,
): DockerExec =
    docker(DockerImage { "busybox" }, renderer, inputStream, scriptInit)


/*
 * CURL & JQ
 */
@Suppress("SpellCheckingInspection")
private val curlJqImage = DockerImage { "dwdraju" / "alpine-curl-jq" digest "sha256:5f6561fff50ab16cba4a9da5c72a2278082bcfdca0f72a9769d7e78bdc5eb954" }

/**
 * Builds a shell script using the given [scriptInit] and runs it in
 * a [alpine-curl-jq](https://hub.docker.com/dwdraju/alpine-curl-jq) based [DockerContainer].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container and also passed to [scriptInit] as the only argument.
 */
public fun Path.curlJq(
    renderer: RendererProvider? = null,
    scriptInit: ScriptInitWithWorkingDirectory,
): DockerExec =
    docker(curlJqImage, renderer, null, scriptInit)

/**
 * Runs a [curl](https://curl.se/docs/manpage.html) with the given [arguments] in
 * a [alpine-curl-jq](https://hub.docker.com/dwdraju/alpine-curl-jq) based [DockerContainer].
 *
 * `this` [Path] is used as the working directory on this host and
 * is mapped to `/work`, which is configured as the working directory
 * inside of the container.
 */
public fun Path.curl(
    vararg arguments: Any,
    renderer: RendererProvider? = null,
): DockerExec =
    docker(curlJqImage, "curl", *arguments, renderer = renderer)

/**
 * Downloads the given [uri] to [fileName] (automatically determined if not specified) in `this` directory using
 * a [alpine-curl-jq](https://hub.docker.com/dwdraju/alpine-curl-jq) based [DockerContainer].
 */
public fun Path.download(
    uri: String,
    fileName: String? = null,
    renderer: RendererProvider? = null,
): Path =
    if (fileName != null) {
        resolve(fileName).also { curl("--location", uri, "-o", fileName, renderer = renderer) }
    } else {
        val downloadDir = randomDirectory()
        downloadDir.run {
            curl("--location", "--remote-name", "--remote-header-name", "--compressed", uri, renderer = renderer)
            listDirectoryEntriesRecursively().singleOrNull()?.let { file ->
                file.moveTo(parent.resolve(file.cleanFileName()))
            } ?: throw FileNotFoundException("Failed to download $uri")
        }.also { downloadDir.deleteRecursively() }
    }

/**
 * Downloads the given [uri] to [fileName] (automatically determined if not specified) in `this` directory using
 * a [alpine-curl-jq](https://hub.docker.com/dwdraju/alpine-curl-jq) based [DockerContainer].
 */
public fun Path.download(
    uri: URI,
    fileName: String? = null,
    renderer: RendererProvider? = null,
): Path =
    download(uri.toString(), fileName, renderer)

private fun Path.cleanFileName(): String = listOf("?", "#").fold(fileName.pathString) { acc, symbol -> acc.substringBefore(symbol) }


/*
 * DOCKER-PI
 */

/**
 * Boots `this` ARM based image using
 * a [dockerpi](https://hub.docker.com/lukechilds/dockerpi) based [DockerContainer]
 * and processes the [IO] with the given [processor].
 */
@Suppress("SpellCheckingInspection")
public fun Path.dockerPi(
    name: String = "dockerpi".withRandomSuffix(),
    renderer: RendererProvider? = null,
    processor: DockerExec.(IO) -> Unit,
): DockerExec =
    DockerRunCommandLine {
        image { "lukechilds" / "dockerpi" tag "vm" }
        options {
            name { name }
            mounts { this@dockerPi mountAt "/sdcard/filesystem.img" }
        }
    }.exec.mode { async(Interactive { nonBlocking }) }.processing(renderer = renderer, processor = processor)
