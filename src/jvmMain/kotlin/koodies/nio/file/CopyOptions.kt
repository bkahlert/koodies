package koodies.io.file

import java.nio.file.CopyOption
import java.nio.file.StandardCopyOption

public object CopyOptions {
    public fun enumArrayOf(
        replaceExisting: Boolean = false,
        copyAttributes: Boolean = false,
        atomicMove: Boolean = false,
        vararg copyOptions: CopyOption,
    ): Array<CopyOption> {
        return listOf(
            replaceExisting to StandardCopyOption.REPLACE_EXISTING,
            copyAttributes to StandardCopyOption.COPY_ATTRIBUTES,
            atomicMove to StandardCopyOption.ATOMIC_MOVE,
        ).mapNotNull { (active, option) -> if (active) option else null }
            .run { plus(copyOptions) }
            .toTypedArray()
    }
}
