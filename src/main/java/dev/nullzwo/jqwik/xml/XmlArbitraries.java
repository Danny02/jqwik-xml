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

    private static final XSConfig DEFAULT_CONFIG = new XSConfig();

    static {
        DEFAULT_CONFIG.minimumElementsGenerated = 1;
        DEFAULT_CONFIG.maximumElementsGenerated = 4;

        DEFAULT_CONFIG.minimumListItemsGenerated = 1;
        DEFAULT_CONFIG.maximumListItemsGenerated = 4;

        DEFAULT_CONFIG.generateOptionalElements = null; // null means random
        DEFAULT_CONFIG.generateOptionalAttributes = null; // null means random
        DEFAULT_CONFIG.generateDefaultAttributes = null; // null means random
        DEFAULT_CONFIG.generateFixedAttributes = null; // null means random
        DEFAULT_CONFIG.generateAllChoices = true;
    }

    private static final Map<String, XSModel> models = new ConcurrentHashMap<>();

    public static Arbitrary<String> fromXsdFile(String xsdFile, String rootName) {
        return Arbitraries.randomValue(r -> generate(r, xsdFile, rootName));
    }

    private static String generate(Random rnd, String xsdFile, String rootName) {
        var xsModel = loadModel(xsdFile);

        QName rootElement = new QName("", rootName);

        try {
            var out = new ByteArrayOutputStream();
            var sampleXml = new XMLDocument(new StreamResult(out), true, 4, null);
            var xsInstance = new XSInstance(new RandomUtil(rnd), DEFAULT_CONFIG);
            xsInstance.generate(xsModel, rootElement, sampleXml);

            return new String(out.toByteArray());
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
