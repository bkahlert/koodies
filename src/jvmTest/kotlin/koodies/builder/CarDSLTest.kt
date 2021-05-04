package koodies.builder

import koodies.debug.CapturedOutput
import koodies.test.SystemIOExclusive
import koodies.test.output.OutputCaptureExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import strikt.api.expectThat
import strikt.assertions.contains

@ExtendWith(OutputCaptureExtension::class)
class CarDSLTest {

    @Test
    @SystemIOExclusive
    fun `should print cars`(output: CapturedOutput) {
        CarDSL().printSamples()
        val w1 = "⌀ 40.64cm"
        val w2 = "⌀ 35.56cm"
        expectThat(output.outLines).contains(
            "Car(name=Exclusive Car, color=hsv(198, 82, 89), traits=[Exclusive, TaxExempt], engine=244.0km/h, 145.0kW, wheels=[$w1, $w1, $w1, $w1])",
            "Car(name=Average Car, color=hsv(0, 0, 0), traits=[], engine=244.0km/h, 145.0kW, wheels=[$w2, $w2, $w2, $w2])",
        )
    }
}
