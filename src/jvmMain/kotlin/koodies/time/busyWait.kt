package koodies.time

import koodies.runtime.JVM
import kotlin.time.Duration
import kotlin.time.milliseconds

public fun Duration.busyWait(sleepIntervals: Duration = 50.milliseconds) {
    val start = System.currentTimeMillis()
    @Suppress("ControlFlowWithEmptyBody")
    while (notPassedSince(start)) {
        try {
            sleepIntervals.sleep()
        } catch (e: InterruptedException) {
            if (passedSince(start)) {
                JVM.currentThread.interrupt()
                break
            }
        }
    }
}
