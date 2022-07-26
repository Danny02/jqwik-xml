package dev.nullzwo.jqwik.xml;

import jlibs.core.util.RandomUtil;
import jlibs.xml.sax.XMLDocument;
import jlibs.xml.xsd.XSConfig;
import jlibs.xml.xsd.XSInstance;
import jlibs.xml.xsd.XSParser;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import org.apache.xerces.xs.XSModel;

import javax.xml.namespace.QName;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class XmlArbitraries {

    public enum OptionalAttributes {
        NULL_ARGUMENTS,
        EMPTY_ARGUMENTS,
        BOTH;
    }

    private final XSConfig config = new XSConfig();

    private static final Map<String, XSModel> models = new ConcurrentHashMap<>();

    public static Arbitrary<byte[]> fromXsdFile(String xsdFile, String rootName, OptionalAttributes config) {
        final Boolean value;
        switch (config) {
            case BOTH:
                value = null;
                break;
            case NULL_ARGUMENTS:
                value = false;
                break;
            case EMPTY_ARGUMENTS:
                value = true;
                break;
            default:
                throw new UnsupportedOperationException();
        }

        return fromXsdFile(xsdFile, rootName, value);
    }

    private static Arbitrary<byte[]> fromXsdFile(String xsdFile, String rootName, Boolean optionalAsEmptyString) {
        var config = new XSConfig();
        config.generateOptionalAttributesAsEmptyString = optionalAsEmptyString;
        return Arbitraries.randomValue(r -> generate(r, xsdFile, rootName, config));
    }

    private static byte[] generate(Random rnd, String xsdFile, String rootName, XSConfig config) {
        var xsModel = loadModel(xsdFile);

        QName rootElement = new QName("", rootName);

        try {
            var out = new ByteArrayOutputStream();
            var sampleXml = new XMLDocument(new StreamResult(out), true, 4, null);
            var xsInstance = new XSInstance(new RandomUtil(rnd), config);
            xsInstance.generate(xsModel, rootElement, sampleXml);

            return out.toByteArray();
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static XSModel loadModel(String xsdFile) {
        var m = models.get(xsdFile);
        if (m == null) {
            m = new XSParser().parse(xsdFile);
            models.put(xsdFile, m);
        }
        return m;
    }
}
