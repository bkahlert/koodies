package koodies.docker

import koodies.collections.to
import koodies.concurrent.process.IO
import koodies.concurrent.process.Process.ExitState
import koodies.concurrent.process.Process.ProcessState.Terminated
import koodies.concurrent.process.status
import koodies.docker.DockerExitStateHandler.Failure.BadRequest
import koodies.docker.DockerExitStateHandler.Failure.BadRequest.CannotRemoveRunningContainer
import koodies.docker.DockerExitStateHandler.Failure.BadRequest.Conflict
import koodies.docker.DockerExitStateHandler.Failure.BadRequest.NameAlreadyInUse
import koodies.docker.DockerExitStateHandler.Failure.BadRequest.NoSuchContainer
import koodies.docker.DockerExitStateHandler.Failure.BadRequest.NoSuchImage
import koodies.docker.DockerExitStateHandler.Failure.BadRequest.PathDoesNotExistInsideTheContainer
import koodies.docker.DockerExitStateHandler.Failure.UnknownError
import koodies.docker.DockerExitStateHandler.ParseException
import koodies.test.test
import koodies.test.testEach
import koodies.text.ANSI.ansiRemoved
import koodies.text.Semantics.Symbols.Negative
import koodies.text.Semantics.formattedAs
import koodies.text.ansiRemoved
import koodies.text.rightSpaced
import koodies.text.spaced
import koodies.text.toStringMatchesCurlyPattern
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD
import strikt.api.Assertion.Builder
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isNotNull
import strikt.assertions.message
import kotlin.reflect.KClass

/**
 * If this class is sealed returns a [Map] where keys are
 * immediate subclasses of this class is a sealed class and values
 * are produced by the given [valueSelector] function applied to each element,
 * or an empty list otherwise.
 *
 * If any two elements are equal, the last one gets added to the map.
 *
 * The returned map preserves the entry iteration order of the original collection.
 */
public inline fun <reified T : Any, V> associateSealedSubclasses(valueSelector: (KClass<out T>) -> V): Map<KClass<out T>, V> =
    T::class.sealedSubclasses.associateWith(valueSelector)

@Execution(SAME_THREAD)
class DockerExitStateHandlerTest {

    private fun getTerminated(errorMessage: String) = Terminated(12345L, 42, listOf(IO.ERR typed errorMessage))

    @TestFactory
    fun `should match bad request error message22`() = listOf(
        NoSuchContainer::class to "Error: No such container: abcd/defg" to "no such container",
    ).testEach { (clazz: KClass<out BadRequest>, errorMessage: String, status: String) ->
        val badRequestState = DockerExitStateHandler.handle(getTerminated(errorMessage))
        expect("matches ${clazz.simpleName}") { badRequestState::class }.that { isEqualTo(clazz) }
        expect("status is abcd/defg") { badRequestState.status.ansiRemoved }.that { isEqualTo("abcd/defg") }
        expect("formatted state ${badRequestState.format()}") { badRequestState.format().ansiRemoved }.that { isEqualTo("$status${Negative.spaced.ansiRemoved}abcd/defg") }
        expect("toString() is ${toString()}") { badRequestState }.that { toStringMatchesCurlyPattern("${Negative.rightSpaced.ansiRemoved}${status.formattedAs.error}") }
    }

    @TestFactory
    fun `should match bad request error message`() = listOf(
        NoSuchContainer::class to "Error: No such container: abcd/defg" to "no such container",
        NoSuchImage::class to "Error: No such image: abcd/defg" to "no such image",
        PathDoesNotExistInsideTheContainer::class to "Error: Path does not exist inside the container: abcd/defg" to "path does not exist inside the container",
        NameAlreadyInUse::class to "Error: Name already in use: abcd/defg" to "name already in use",
        Conflict::class to "Error: Conflict: abcd/defg" to "conflict"
    ).testEach { (clazz: KClass<out BadRequest>, errorMessage: String, status: String) ->
        val badRequestState = DockerExitStateHandler.handle(getTerminated(errorMessage))

        expect("matches ${clazz.simpleName}") { badRequestState::class }.that { isEqualTo(clazz) }
        expect("status is abcd/defg") { badRequestState.status.ansiRemoved }.that { isEqualTo("abcd/defg") }
        expect("formatted state ${badRequestState.format()}") { badRequestState.format().ansiRemoved }.that { isEqualTo("$status${Negative.spaced.ansiRemoved}abcd/defg") }
        expect("toString() is ${toString()}") { badRequestState }.that { toStringMatchesCurlyPattern("${Negative.rightSpaced.ansiRemoved}${status.formattedAs.error}") }
    }

    @TestFactory
    fun `should match cannot remove running container error messages`() = test(
        "Error response from daemon: You cannot remove a running container 2c5e082a462134. " +
            "Stop the container before attempting removal or force remove") { errorMessage ->

        val status = "You cannot remove a running container. Stop the container before attempting removal or force remove."
        val response = DockerExitStateHandler.handle(getTerminated(errorMessage))

        expect("matches ${CannotRemoveRunningContainer::class.simpleName}") { response::class }.that { isEqualTo(CannotRemoveRunningContainer::class) }
        expect("status is abcd/defg") { response.status.ansiRemoved }.that { isEqualTo(status) }
        expect("formatted state ${response.format()}") { response.format().ansiRemoved }.that { isEqualTo("${Negative.rightSpaced.ansiRemoved}$status") }
        expect("toString() is ${toString()}") { response }.that { toStringMatchesCurlyPattern("${Negative.rightSpaced.ansiRemoved}$status") }
    }


    @Test
    fun `should return unknown state for unknown error`() {
        val unknownError = DockerExitStateHandler.handle(getTerminated("Error: Nothing I know of: status"))
        expectThat(unknownError).isA<UnknownError>().and {
            status.ansiRemoved.isEqualTo("Unknown error from Docker daemon: Nothing I know of: status")
            get { format() }.ansiRemoved.isEqualTo("ϟ Unknown error from Docker daemon: Nothing I know of: status")
            toStringMatchesCurlyPattern("Unknown error from Docker daemon: Nothing I know of: status")
        }
    }

    @Test
    fun `should throw on error-like message without error prefix`() {
        expectCatching { DockerExitStateHandler.handle(getTerminated("No Error: No such container: abcd/defg")) }.isFailure().isA<ParseException>().and {
            message.isNotNull().ansiRemoved.isEqualTo("Error parsing response from Docker daemon: No Error: No such container: abcd/defg")
        }
    }

    @Test
    fun `should throw if exception is caught`() {
        expectCatching { DockerExitStateHandler.handle(getTerminated("Not the typical error")) }.isFailure().isA<ParseException>().and {
            message.isNotNull().ansiRemoved.isEqualTo("Error parsing response from Docker daemon: Not the typical error")
        }
    }
}

public fun Builder<ExitState>.isSuccessful(): Builder<ExitState> =
    assert("exit state represents success") { actual ->
        when (actual.successful) {
            true -> pass(actual.successful)
            null -> fail("process did not terminate,yet")
            false -> fail("process failed: $actual")
        }
    }


public fun Builder<ExitState>.isFailed(): Builder<ExitState> =
    assert("exit state represents failed") { actual ->
        when (actual.successful) {
            true -> fail("process did not fail: $actual")
            null -> fail("process did not terminate,yet")
            false -> pass(actual.successful)
        }
    }
