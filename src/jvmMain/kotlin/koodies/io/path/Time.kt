package koodies.io.path

import koodies.time.Now
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.setLastModifiedTime
import kotlin.time.Duration

public fun Path.touch(): Path {
    require(parent.exists())
    if (exists()) setLastModifiedTime(Now.fileTime)
    else createFile()
    return this
}

/**
 * Contains since when this file was last modified.
 */
public var Path.age: Duration
    get() :Duration = Duration.milliseconds((Now.millis - getLastModifiedTime().toMillis()))
    set(value) {
        setLastModifiedTime(FileTime.from(Now.minus(value)))
    }
