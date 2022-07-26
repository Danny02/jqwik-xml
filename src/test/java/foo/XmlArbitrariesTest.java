package foo;

import dev.nullzwo.jqwik.xml.XmlArbitraries;
import net.jqwik.api.Arbitrary;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static dev.nullzwo.jqwik.xml.XmlArbitraries.OptionalAttributes.BOTH;
import static org.assertj.core.api.Assertions.assertThat;


public class XmlArbitrariesTest {
    Arbitrary<byte[]> arb = XmlArbitraries.fromXsdFile("src/test/resources/example.xsd", "shiporder", BOTH);

    @Test
    void shouldSameValueForSameRandom() {
        var one = arb.generator(1).next(new Random(0)).value();
        var two = arb.generator(1).next(new Random(0)).value();

        assertThat(two).isEqualTo(one);
    }
}
