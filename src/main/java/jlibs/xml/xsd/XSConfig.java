package jlibs.xml.xsd;

public class XSConfig {

    public int minimumElementsGenerated = 1;
    public int maximumElementsGenerated = 4;
    public int minimumListItemsGenerated = 1;
    public int maximumListItemsGenerated = 4;
    public Boolean generateOptionalElements = null;
    public Boolean generateOptionalAttributes = null;

    public Boolean generateOptionalAttributesAsEmptyString = Boolean.FALSE;
    public Boolean generateFixedAttributes = null;
    public Boolean generateDefaultAttributes = null;
    public boolean generateAllChoices = true;
    public boolean showContentModel = true;


}
