package koodies.tracing.rendering

import koodies.text.Semantics.formattedAs
import koodies.tracing.Key
import kotlin.math.floor

/**
 * Specialized component that focuses on computing column-based layouts.
 */
public data class ColumnsLayout(

    /**
     * The columns to be used.
     */
    public val columns: List<Column> = listOf(Column(RenderingAttributes.DESCRIPTION, 80)),

    /**
     * The gap between two neighbouring columns.
     */
    public val gap: Int = 5,

    /**
     * Sum of all columns and gaps. If specified explicitly, the provided columns are scaled accordingly
     * to meet this value.
     */
    public val totalWidth: Int = columns.sum(gap),
) {

    public constructor(

        /**
         * The columns to be used.
         */
        vararg columns: Column = arrayOf(RenderingAttributes.DESCRIPTION columns 80),

        /**
         * The gap between two neighbouring columns.
         */
        gap: Int = 5,

        /**
         * Sum of all columns and gaps. If specified explicitly, the provided columns are scaled accordingly
         * to meet this value.
         */
        maxColumns: Int = columns.toList().sum(gap),
    ) : this(columns.toList(), gap, maxColumns)

    init {
        require(columns.isNotEmpty()) { "At least one column must be specified." }
    }

    /**
     * The column with the highest importance.
     */
    public val primaryKey: Key<*, *> get() = columns.maxByOrNull { it.width }?.key ?: columns.first().key

    /**
     * Contains the [columns] scaled proportionally.
     */
    public val scaled: Map<Key<*, *>, Int> = run {
        if (columns.isEmpty()) emptyMap()
        else {
            val gaps = (columns.size - 1).coerceAtLeast(0) * gap
            val requestedColumns = columns.sumOf { it.width }
            val factor = (totalWidth - gaps).toDouble() / requestedColumns
            val distributed = columns.map { it.key to floor(it.width * factor).toInt() }.toMutableList()
            val actualColumns = distributed.sumOf { (_, c) -> c } + gaps
            distributed[0] = distributed[0].let { (name, c) -> name to c + totalWidth - actualColumns }
            distributed.toMap()
        }
    }

    /**
     * Extracts the column attribute keys contained in [columns] from the given [attributes]
     * mapped to their scaled column size. Attribute keys not present in the given [attributes] are mapped to `null`.
     */
    public fun extract(attributes: RenderableAttributes): List<Pair<Renderable?, Int>> =
        scaled.map { (attributeKey, width) -> attributes[attributeKey] to width }

    /**
     * Returns a copy of this configuration [columns] narrower.
     *
     * Only the left column is reduced in width so all columns stay aligned.
     * If no more columns are available, a [IllegalArgumentException] is thrown.
     */
    public fun shrinkBy(columns: Int): ColumnsLayout {
        val firstColumn = this.columns.first().width
        require(columns < firstColumn) { "Columns cannot be shrunk by ${columns.formattedAs.input} as the leftmost column is only $firstColumn wide." }
        val mapIndexed: List<Column> = this.columns.mapIndexed { index, (name, width) ->
            if (index == 0) Column(name, width - columns)
            else Column(name, width)
        }
        return ColumnsLayout(mapIndexed, gap, totalWidth - columns)
    }

    /** A column specified by its [key] and its [width]. */
    public data class Column(public val key: Key<*, *>, public val width: Int) {
        public constructor(spec: Pair<Key<*, *>, Int>) : this(spec.first, spec.second)
    }

    public companion object {

        private fun List<Column>.sum(gap: Int): Int =
            sumOf { it.width } + (size - 1).coerceAtLeast(0) * gap

        /**
         * Creates a [Column] with [width] describing the amount of columns taken up.
         */
        public infix fun Key<*, *>.columns(width: Int): Column = Column(this, width)
    }
}
