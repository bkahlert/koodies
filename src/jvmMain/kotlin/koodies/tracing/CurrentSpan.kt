package koodies.tracing

import koodies.tracing.Key.KeyValue
import koodies.tracing.rendering.RenderingAttributes

@DslMarker
public annotation class TracingDsl

/**
 * The currently active span.
 */
@TracingDsl
public interface CurrentSpan {

    /** Records the given [event]. */
    public fun event(event: Event): CurrentSpan

    /** Records an event using the given [name] and optional [attributes]. */
    public fun event(
        name: CharSequence,
        vararg attributes: KeyValue<*, *>,
    ): CurrentSpan = event(Event.of(name, *attributes))

    /**
     * Records an event using the given [description] and optional [attributes].
     *
     * Attributes with a `null` value are removed; and together with the [description] rendered.
     *
     * ***Note:** This is a convenience method to facilitate migrating from an existing logger.
     * The effectively required event name is derived from the description.
     * This can lead to a high cardinality (esp. if the description contains variables).
     * If too many different event names are created the value of the recorded data for later analysis is considerably reduced.
     * Consider using [event] instead.*
     */
    public fun log(
        description: CharSequence,
        vararg attributes: KeyValue<*, *>,
    ): CurrentSpan = event("log", RenderingAttributes.DESCRIPTION to description, *attributes)

    /** Records the given [exception] using the given optional [attributes]. */
    public fun exception(exception: Throwable, vararg attributes: KeyValue<*, *>): CurrentSpan
}

public interface Event {

    /**
     * Name of this event.
     */
    public val name: CharSequence

    /**
     * Attributes describing this event.
     */
    public val attributes: Set<KeyValue<*, *>>

    private data class SimpleEvent(
        override val name: CharSequence,
        override val attributes: Set<KeyValue<*, *>>,
    ) : Event

    public companion object {
        public fun of(name: CharSequence, vararg attributes: KeyValue<*, *>): Event =
            SimpleEvent(name, attributes.toSet())
    }
}
