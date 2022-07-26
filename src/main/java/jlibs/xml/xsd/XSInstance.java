/**
 * JLibs: Common Utilities for Java
 * Copyright (C) 2009  Santhosh Kumar T <santhosh.tekuri@gmail.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package jlibs.xml.xsd;

import jlibs.core.graph.*;
import jlibs.core.graph.navigators.FilteredTreeNavigator;
import jlibs.core.graph.sequences.DuplicateSequence;
import jlibs.core.graph.sequences.IterableSequence;
import jlibs.core.graph.sequences.RepeatSequence;
import jlibs.core.graph.visitors.ReflectionVisitor;
import jlibs.core.graph.walkers.PreorderWalker;
import jlibs.core.lang.ImpossibleException;
import jlibs.core.util.RandomUtil;
import jlibs.xml.Namespaces;
import jlibs.xml.sax.XMLDocument;
import jlibs.xml.xsd.display.XSDisplayFilter;
import org.apache.xerces.xs.*;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Santhosh Kumar T
 */
@SuppressWarnings({"unchecked"})
public class XSInstance{
    
    private final RandomUtil randomUtil;
    private final XSConfig config;

    public XSInstance(RandomUtil randomUtil, XSConfig config) {
        this.randomUtil = randomUtil;
        this.config = config;
    }

    private int generateRepeatCount(int minOccurs, int maxOccurs){
        if(minOccurs==0 && maxOccurs==1) //optional case
            return randomUtil.randomBoolean(config.generateOptionalElements) ? 1 : 0;

        if(maxOccurs==-1)
            maxOccurs = Math.max(minOccurs, config.maximumElementsGenerated);

        int min, max;
        if(config.minimumElementsGenerated>maxOccurs || config.maximumElementsGenerated<minOccurs){ // doesn't intersect
            min = minOccurs;
            max = maxOccurs;
        }else { // find intersecting range
            min = Math.max(minOccurs, config.minimumElementsGenerated);
            max = Math.min(maxOccurs, config.maximumElementsGenerated);
        }
        return (min == max)
                ? min
                : randomUtil.random(min, max);
    }

    public void generate(XSModel xsModel, QName rootElement, XMLDocument doc){
        String namespace = rootElement.getNamespaceURI();
        XSElementDeclaration root = xsModel.getElementDeclaration(rootElement.getLocalPart(), namespace);
        if(root==null)
            throw new IllegalArgumentException("Element "+rootElement+" is not found");

        Navigator navigator = new FilteredTreeNavigator(new XSSampleNavigator(xsModel), new XSDisplayFilter(){
            private boolean process(XSElementDeclaration elem){
                return !elem.getAbstract();
            }

            protected boolean process(XSTypeDefinition type){
                return type.getTypeCategory()==XSTypeDefinition.COMPLEX_TYPE;
            }
        });
        try{
            doc.startDocument();
            doc.declarePrefix(Namespaces.URI_XSI);
            if(!rootElement.getNamespaceURI().isEmpty())
                doc.declarePrefix(rootElement.getPrefix(), rootElement.getNamespaceURI());
            WalkerUtil.walk(new PreorderWalker(root, navigator), new XSSampleVisitor(doc));
            doc.endDocument();
        }catch(SAXException ex){
            throw new ImpossibleException(ex);
        }
    }

    private class XSSampleNavigator extends XSNavigator {
        private final XSModel xsModel;
        private XSSampleNavigator(XSModel xsModel){
            this.xsModel = xsModel;
        }

        protected Sequence<XSTerm> process(XSParticle particle){
            XSTerm term = particle.getTerm();
            if(term instanceof XSModelGroup){
                XSModelGroup group = (XSModelGroup) term;
                if(group.getCompositor()==XSModelGroup.COMPOSITOR_CHOICE){
                    XSObjectList particles = group.getParticles();
                    int count = particles.getLength();
                    if(!config.generateAllChoices && !particle.getMaxOccursUnbounded())
                        count = Math.min(count, particle.getMaxOccurs());
                    List<XSParticle> list = new ArrayList<XSParticle>(particles.getLength());
                    for(int i=0; i<particles.getLength(); i++)
                        list.add((XSParticle)particles.item(i));
                    Collections.shuffle(list);
                    return new IterableSequence(list.subList(0, count));
                }
            }

            int maxOccurs = particle.getMaxOccursUnbounded() ? -1 : particle.getMaxOccurs();
            int repeatCount = generateRepeatCount(particle.getMinOccurs(), maxOccurs);
            return new RepeatSequence<XSTerm>(super.process(particle), repeatCount);
        }

        protected Sequence<XSParticle> process(XSModelGroup modelGroup){
            String str;
            if (modelGroup.getCompositor() == XSModelGroup.COMPOSITOR_ALL) {
                XSObjectList particles = modelGroup.getParticles();
                List<XSParticle> list = new ArrayList<XSParticle>(particles.getLength());
                for (int i = 0; i < particles.getLength(); i++)
                    list.add((XSParticle) particles.item(i));
                Collections.shuffle(list);
                return new IterableSequence<XSParticle>(list);
            }
            return super.process(modelGroup);
        }

        protected Sequence process(XSElementDeclaration elem){
            if(elem.getAbstract()){
                XSObjectList substitutionGroup = xsModel.getSubstitutionGroup(elem);
                int rand = randomUtil.random(0, substitutionGroup.getLength() - 1);
                return new DuplicateSequence(substitutionGroup.item(rand));
            }
            if(elem.getTypeDefinition() instanceof XSComplexTypeDefinition){
                XSComplexTypeDefinition complexType = (XSComplexTypeDefinition) elem.getTypeDefinition();
                if(complexType.getAbstract()){
                    List<XSComplexTypeDefinition> subTypes = XSUtil.getSubTypes(xsModel, complexType);
                    int rand = randomUtil.random(0, subTypes.size() - 1);
                    return new DuplicateSequence<XSTypeDefinition>(subTypes.get(rand));
                }
            }
            return new DuplicateSequence<XSTypeDefinition>(elem.getTypeDefinition());
        }
    }

    private class XSSampleVisitor extends ReflectionVisitor<Object, Processor<Object>>{
        private final XMLDocument doc;
        private XSSampleVisitor(XMLDocument doc){
            this.doc = doc;
        }

        @Override
        protected Processor getDefault(Object elem){
            return null;
        }

        protected Processor process(XSElementDeclaration elem){
            return elemProcessor;
        }

        protected Processor process(XSWildcard wildcard){
            return wildcardProcessor;
        }

        protected Processor process(XSComplexTypeDefinition complexType){
            return complexTypeProcessor;
        }

        protected Processor process(XSAttributeUse attr){
            return attrProcessor;
        }

        private final Processor<XSElementDeclaration> elemProcessor = new Processor<XSElementDeclaration>(){
            @Override
            public boolean preProcess(XSElementDeclaration elem, Path path){
                if(path.getRecursionDepth()>2)
                    return false;
                try{
                    if(config.showContentModel && elem.getTypeDefinition() instanceof XSComplexTypeDefinition){
                        XSComplexTypeDefinition complexType = (XSComplexTypeDefinition) elem.getTypeDefinition();
                        switch(complexType.getContentType()){
                            case XSComplexTypeDefinition.CONTENTTYPE_ELEMENT:
                            case XSComplexTypeDefinition.CONTENTTYPE_MIXED:
                                String contentModel = new XSContentModel().toString(complexType, doc.getNamespaceSupport());
                                boolean showContentModel = false;
                                for(char ch: "?*+|;[".toCharArray()){
                                    if(contentModel.indexOf(ch)!=-1){
                                        showContentModel = true;
                                        break;
                                    }
                                }
                                if(showContentModel){
                                    int depth = 0;
                                    while(true){
                                        path = path.getParentPath(XSElementDeclaration.class);
                                        if(path!=null)
                                            depth++;
                                        else
                                            break;
                                    }
                                    doc.addText("\n");
                                    for(int i=depth; i>0; i--)
                                        doc.addText("   ");
                                    doc.addComment(contentModel);
                                    doc.addText("\n");
                                    for(int i=depth; i>0; i--)
                                        doc.addText("   ");
                                }
                        }
                    }
                    doc.startElement(elem.getNamespace(), elem.getName());
                    return true;
                }catch(SAXException ex){
                    throw new ImpossibleException(ex);
                }
            }

            @Override
            public void postProcess(XSElementDeclaration elem, Path path){
                if(path.getRecursionDepth()>2)
                    return;
                try{
                    XSSimpleTypeDefinition simpleType = null;
                    if(elem.getTypeDefinition().getTypeCategory()==XSTypeDefinition.SIMPLE_TYPE)
                        simpleType = (XSSimpleTypeDefinition)elem.getTypeDefinition();
                    else{
                        XSComplexTypeDefinition complexType = (XSComplexTypeDefinition)elem.getTypeDefinition();
                        if(complexType.getContentType()==XSComplexTypeDefinition.CONTENTTYPE_SIMPLE)
                            simpleType = complexType.getSimpleType();
                    }
                    if(simpleType!=null)
                        doc.addText(generateSampleValue(simpleType, elem.getName()));
                    doc.endElement();
                }catch(SAXException ex){
                    throw new ImpossibleException(ex);
                }
            }
        };

        private final Processor<XSAttributeUse> attrProcessor = new Processor<XSAttributeUse>(){
            @Override
            public boolean preProcess(XSAttributeUse attr, Path path){
                try{
                    XSAttributeDeclaration decl = attr.getAttrDeclaration();

                    String sampleValue = null;
                    switch(attr.getConstraintType()){
                        case XSConstants.VC_FIXED:
                            if(randomUtil.randomBoolean(config.generateFixedAttributes))
                                sampleValue = attr.getConstraintValue();
                            break;
                        case XSConstants.VC_DEFAULT:
                            if(randomUtil.randomBoolean(config.generateDefaultAttributes))
                                sampleValue = attr.getConstraintValue();
                            break;
                        default:
                            if(attr.getRequired() || randomUtil.randomBoolean(config.generateOptionalAttributes))
                                sampleValue = generateSampleValue(decl.getTypeDefinition(), decl.getName());
                            else
                                sampleValue = randomUtil.randomBoolean(config.generateOptionalAttributesAsEmptyString) ? "" : null;
                    }
                    if(sampleValue!=null)
                        doc.addAttribute(decl.getNamespace(), decl.getName(), sampleValue);
                    return false;
                }catch(SAXException ex){
                    throw new ImpossibleException(ex);
                }
            }

            @Override
            public void postProcess(XSAttributeUse elem, Path path){}
        };

        private final Processor<XSComplexTypeDefinition> complexTypeProcessor = new Processor<XSComplexTypeDefinition>(){
            @Override
            public boolean preProcess(XSComplexTypeDefinition complexType, Path path){
                try{
                    XSElementDeclaration elem = (XSElementDeclaration)path.getParentPath().getElement();
                    XSComplexTypeDefinition elemType = (XSComplexTypeDefinition)elem.getTypeDefinition();
                    if(elemType.getAbstract())
                        doc.addAttribute(Namespaces.URI_XSI, "type", doc.toQName(complexType.getNamespace(), complexType.getName()));
                    return true;
                }catch(SAXException ex){
                    throw new ImpossibleException(ex);
                }
            }

            @Override
            public void postProcess(XSComplexTypeDefinition complexType, Path path){}
        };

        private final Processor<XSWildcard> wildcardProcessor = new Processor<XSWildcard>(){
            @Override
            public boolean preProcess(XSWildcard wildcard, Path path){
                try{
                    String uri;
                    switch(wildcard.getConstraintType()){
                        case XSWildcard.NSCONSTRAINT_ANY:
                            uri = "anyNS";
                            break;
                        case XSWildcard.NSCONSTRAINT_LIST:
                            StringList list = wildcard.getNsConstraintList();
                            int rand = randomUtil.random(0, list.getLength()-1);
                            uri = list.item(rand);
                            if(uri==null)
                                uri = ""; // <xs:any namespace="##local"/> returns nsConstraintList with null
                            break;
                        case XSWildcard.NSCONSTRAINT_NOT:
                            list = wildcard.getNsConstraintList();
                            List<String> namespaces = new ArrayList<String>();
                            for(int i=0; i<list.getLength(); i++)
                                namespaces.add(list.item(i));
                            uri = "anyNS";
                            if(namespaces.contains(uri)){
                                for(int i=1;;i++){
                                    if(!namespaces.contains(uri+i)){
                                        uri += i;
                                        break;
                                    }
                                }
                            }
                            break;
                        default:
                            throw new ImpossibleException();
                    }
                    if(isAttribute(wildcard, path))
                        doc.addAttribute(uri, "anyAttr", "anyValue");
                    else
                        doc.startElement(uri, "anyElement");
                    return true;
                }catch(SAXException ex){
                    throw new ImpossibleException(ex);
                }
            }

            @Override
            public void postProcess(XSWildcard wildcard, Path path){
                try{
                    if(!isAttribute(wildcard, path))
                        doc.endElement();
                }catch(SAXException ex){
                    throw new ImpossibleException(ex);
                }
            }

            private boolean isAttribute(XSWildcard wildcard, Path path){
                if(path.getParentPath().getElement() instanceof XSComplexTypeDefinition){
                    XSComplexTypeDefinition complexType = (XSComplexTypeDefinition) path.getParentPath().getElement();
                    return complexType.getAttributeWildcard() == wildcard;
                }
                return false;
            }
        };

        private final Map<String, Integer> counters = new HashMap<String, Integer>();

        private static final String XSD_DATE_FORMAT = "yyyy-MM-dd";
        private static final String XSD_TIME_FORMAT = "HH:mm:ss";

        private String generateSampleValue(XSSimpleTypeDefinition simpleType, String hint){
            if(simpleType.getBuiltInKind()==XSConstants.LIST_DT){
                XSSimpleTypeDefinition itemType = simpleType.getItemType();

                int len;
                XSFacet facet = getFacet(itemType, XSSimpleTypeDefinition.FACET_LENGTH);
                if(facet!=null)
                    len = Integer.parseInt(facet.getLexicalFacetValue());
                else{
                    int minOccurs = 0;
                    facet = getFacet(itemType, XSSimpleTypeDefinition.FACET_MINLENGTH);
                    if(facet!=null)
                        minOccurs = Integer.parseInt(facet.getLexicalFacetValue());
                    int maxOccurs = -1;
                    facet = getFacet(itemType, XSSimpleTypeDefinition.FACET_MAXLENGTH);
                    if(facet!=null)
                        maxOccurs = Integer.parseInt(facet.getLexicalFacetValue());

                    if(maxOccurs==-1)
                        maxOccurs = Math.max(minOccurs, config.maximumListItemsGenerated);

                    int min, max;
                    if(config.minimumListItemsGenerated>maxOccurs || config.maximumListItemsGenerated<minOccurs){ // doesn't intersect
                        min = minOccurs;
                        max = maxOccurs;
                    }else { // find intersecting range
                        min = Math.max(minOccurs, config.minimumListItemsGenerated);
                        max = Math.min(maxOccurs, config.maximumListItemsGenerated);
                    }
                    len = (min == max)
                            ? min
                            : randomUtil.random(min, max);
                }

                List<String> enums = XSUtil.getEnumeratedValues(itemType);
                if(enums.isEmpty()){
                    StringBuilder buff = new StringBuilder();
                    while(len>0){
                        buff.append(" ");
                        buff.append(generateSampleValue(itemType, hint));
                        len--;
                    }
                    return buff.toString().trim();
                }else{
                    while(enums.size()<len)
                        enums.addAll(new ArrayList<String>(enums));
                    Collections.shuffle(enums);

                    StringBuilder buff = new StringBuilder();
                    while(len>0){
                        buff.append(" ");
                        buff.append(enums.remove(0));
                        len--;
                    }
                    return buff.toString().trim();
                }
            }else if(simpleType.getMemberTypes().getLength()>0){
                XSObjectList members = simpleType.getMemberTypes();
                int rand = randomUtil.random(0, members.getLength()-1);
                return generateSampleValue((XSSimpleTypeDefinition)members.item(rand), hint);
            }

            List<String> enums = XSUtil.getEnumeratedValues(simpleType);
            if(!enums.isEmpty())
                return enums.get(randomUtil.random(0, enums.size()-1));

            XSSimpleTypeDefinition builtInType = simpleType;
            while(!Namespaces.URI_XSD.equals(builtInType.getNamespace()))
                builtInType = (XSSimpleTypeDefinition)builtInType.getBaseType();


            String name = builtInType.getName().toLowerCase();
            if("boolean".equals(name))
                return randomUtil.randomBoolean() ? "true" : "false";

            if("double".equals(name)
                    || "decimal".equals(name)
                    || "float".equals(name)
                    || name.endsWith("integer")
                    || name.endsWith("int")
                    || name.endsWith("long")
                    || name.endsWith("short")
                    || name.endsWith("byte"))
                return new Range(simpleType).randomNumber();

            if("date".equals(name))
                return new SimpleDateFormat(XSD_DATE_FORMAT).format(new Date());
            if("time".equals(name))
                return new SimpleDateFormat(XSD_TIME_FORMAT).format(new Date());
            if("dateTime".equals(name)){
                Date date = new Date();
                return new SimpleDateFormat(XSD_DATE_FORMAT).format(date)+'T'+new SimpleDateFormat(XSD_TIME_FORMAT).format(date);
            }else{
                Integer count = counters.get(hint);
                count = count==null ? 1 : ++count;
                counters.put(hint, count);
                String countStr = count.toString();
                XSFacet facet = getFacet(simpleType, XSSimpleTypeDefinition.FACET_MINLENGTH);
                if(facet!=null){
                    int len = Integer.parseInt(facet.getLexicalFacetValue());
                    len -= hint.length();
                    len -= countStr.length();
                    if(len>0){
                        char[] ch = new char[len];
                        Arrays.fill(ch, '_');
                        hint += new String(ch);
                    }
                }
                facet = getFacet(simpleType, XSSimpleTypeDefinition.FACET_MAXLENGTH);
                if(facet!=null){
                    int maxLen = Integer.parseInt(facet.getLexicalFacetValue());
                    int len = maxLen;
                    len = hint.length() + countStr.length() - len;
                    if(len>0){
                        if(hint.length()>len)
                            hint = hint.substring(0, hint.length()-len);
                        else{
                            hint = hint.substring(0, maxLen);
                            countStr = "";
                        }
                    }
                }
                return hint+countStr;
            }
        }

        private XSFacet getFacet(XSSimpleTypeDefinition simpleType, int kind){
            XSObjectList facets = simpleType.getFacets();
            for(int i=0; i<facets.getLength(); i++){
                XSFacet facet = (XSFacet)facets.item(i);
                if(facet.getFacetKind()==kind)
                    return facet;
            }
            return null;
        }

        class Range{
            String minInclusive;
            String minExclusive;
            String maxInclusive;
            String maxExclusive;
            int totalDigits = -1;
            int fractionDigits = -1;

            Range(XSSimpleTypeDefinition simpleType){
                XSFacet facet = getFacet(simpleType, XSSimpleTypeDefinition.FACET_MININCLUSIVE);
                if(facet!=null)
                    minInclusive = facet.getLexicalFacetValue();
                facet = getFacet(simpleType, XSSimpleTypeDefinition.FACET_MINEXCLUSIVE);
                if(facet!=null)
                    minExclusive = facet.getLexicalFacetValue();

                facet = getFacet(simpleType, XSSimpleTypeDefinition.FACET_MAXINCLUSIVE);
                if(facet!=null)
                    maxInclusive = facet.getLexicalFacetValue();
                facet = getFacet(simpleType, XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE);
                if(facet!=null)
                    maxExclusive = facet.getLexicalFacetValue();

                facet = getFacet(simpleType, XSSimpleTypeDefinition.FACET_TOTALDIGITS);
                if(facet!=null)
                    totalDigits = Integer.parseInt(facet.getLexicalFacetValue());

                facet = getFacet(simpleType, XSSimpleTypeDefinition.FACET_FRACTIONDIGITS);
                if(facet!=null)
                    fractionDigits = Integer.parseInt(facet.getLexicalFacetValue());
            }

            private String applyDigits(Object obj){
                String str = String.valueOf(obj);
                String number, fraction;
                int dot = str.indexOf(".");
                if(dot==-1){
                    number = str;
                    fraction = "";
                }else{
                    number = str.substring(0, dot);
                    fraction = str.substring(dot+1);
                }
                boolean negative = false;
                if(number.startsWith("-")){
                    negative = true;
                    number = number.substring(1);
                }
                if(totalDigits>=0){
                    if(number.length()>totalDigits)
                        number = number.substring(0, totalDigits);
                }
                if(fractionDigits>=0){
                    if(fraction.length()>fractionDigits)
                        fraction = fraction.substring(0, fractionDigits);
                }

                str = negative ? "-" : "";
                str += number;
                if(fraction.length()>0)
                    str += '.' + fraction;
                return str;
            }

            public String randomNumber(){
                if(fractionDigits==0){
                    // NOTE: min/max facets can have fractional part
                    //       even though fractionDigits is zero
                    long min = Long.MIN_VALUE;
                    if(minInclusive!=null)
                        min = (long)Double.parseDouble(minInclusive);
                    if(minExclusive!=null)
                        min = (long)Double.parseDouble(minExclusive)+1;

                    long max = Long.MAX_VALUE;
                    if(maxInclusive!=null)
                        max = (long)Double.parseDouble(maxInclusive);
                    if(maxExclusive!=null)
                        max = (long)Double.parseDouble(maxExclusive)-1;

                    return applyDigits(randomUtil.random(min, max));
                }else{
                    double min = Double.MIN_VALUE;
                    if(minInclusive!=null)
                        min = Double.parseDouble(minInclusive);
                    if(minExclusive!=null)
                        min = Double.parseDouble(minExclusive)+1;

                    double max = Double.MAX_VALUE;
                    if(maxInclusive!=null)
                        max = Double.parseDouble(maxInclusive);
                    if(maxExclusive!=null)
                        max = Double.parseDouble(maxExclusive)-1;

                    return applyDigits(randomUtil.random(min, max));
                }
            }
        }
    }
}
