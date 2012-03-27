/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.repo.sql.data.common;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.dom.PrismDomProcessor;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.repo.sql.DtoTranslationException;
import com.evolveum.midpoint.repo.sql.type.XMLGregorianCalendarType;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.apache.commons.lang.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.*;

/**
 * @author lazyman
 */
class RAnyConverter {

    private static enum ValueType {
        LONG, STRING, DATE
    }

    private static final Trace LOGGER = TraceManager.getTrace(RAnyConverter.class);
    private static final Map<QName, ValueType> TYPE_MAP = new HashMap<QName, ValueType>();
    private PrismContext prismContext;
    private Document document;

    static {
        TYPE_MAP.put(DOMUtil.XSD_INTEGER, ValueType.LONG);
        TYPE_MAP.put(DOMUtil.XSD_LONG, ValueType.LONG);
        TYPE_MAP.put(DOMUtil.XSD_SHORT, ValueType.LONG);

        TYPE_MAP.put(DOMUtil.XSD_STRING, ValueType.STRING);

        TYPE_MAP.put(DOMUtil.XSD_DATETIME, ValueType.DATE);
    }

    RAnyConverter(PrismContext prismContext) {
        this.prismContext = prismContext;
    }

    Set<RValue> convertToValue(Item item) throws DtoTranslationException {
        Validate.notNull(item, "Object for converting must not be null.");

        Set<RValue> rValues = new HashSet<RValue>();
        try {
            ItemDefinition definition = item.getDefinition();

            RValue rValue = null;
            List<PrismValue> values = item.getValues();
            for (PrismValue value : values) {
                if (value instanceof PrismContainerValue) {
                    rValue = createClobValue((PrismContainerValue) value);
                } else if (value instanceof PrismPropertyValue) {
                    PrismPropertyValue propertyValue = (PrismPropertyValue) value;
                    switch (getValueType(definition.getTypeName())) {
                        case LONG:
                            RLongValue longValue = new RLongValue();
                            longValue.setValue(extractValue(propertyValue, Long.class));
                            rValue = longValue;
                            break;
                        case DATE:
                            RDateValue dateValue = new RDateValue();
                            dateValue.setValue(extractValue(propertyValue, Date.class));
                            rValue = dateValue;
                            break;
                        case STRING:
                        default:
                            if (isIndexable(definition)) {
                                RStringValue strValue = new RStringValue();
                                strValue.setValue(extractValue(propertyValue, String.class));
                                rValue = strValue;
                            } else {
                                rValue = createClobValue(propertyValue);
                            }
                    }
                }

                rValue.setName(definition.getName());
                rValue.setType(definition.getTypeName());
                rValue.setValueType(getValueType(value.getParent()));

                rValues.add(rValue);
            }
        } catch (Exception ex) {
            throw new DtoTranslationException(ex.getMessage(), ex);
        }

        return rValues;
    }

    private boolean isIndexable(ItemDefinition definition) {
        if (definition instanceof PrismContainerDefinition) {
            return false;
        } else if (definition instanceof PrismReferenceDefinition) {
            return true;
        }
        if (!(definition instanceof PrismPropertyDefinition)) {
            throw new UnsupportedOperationException("Unknown definition type '"
                    + definition + "', can't say if it's indexed or not.");
        }

        PrismPropertyDefinition pDefinition = (PrismPropertyDefinition) definition;
        if (pDefinition.isIndexed() != null) {
            return pDefinition.isIndexed();
        }

        QName type = definition.getTypeName();
        return DOMUtil.XSD_DATETIME.equals(type)
                || DOMUtil.XSD_LONG.equals(type)
                || DOMUtil.XSD_SHORT.equals(type)
                || DOMUtil.XSD_INTEGER.equals(type)
                || DOMUtil.XSD_DOUBLE.equals(type)
                || DOMUtil.XSD_FLOAT.equals(type)
                || DOMUtil.XSD_STRING.equals(type);
    }

    private RValueType getValueType(Itemable itemable) {
        Validate.notNull(itemable, "Value parent must not be null.");
        if (!(itemable instanceof Item)) {
            throw new IllegalArgumentException("Item type '" + itemable.getClass() + "' not supported in 'any' now.");
        }

        return RValueType.getTypeFromItemClass(((Item) itemable).getClass());
    }

    private RClobValue createClobValue(PrismContainerValue containerValue) throws SchemaException {
        PrismDomProcessor domProcessor = prismContext.getPrismDomProcessor();
        Element root = createElement(RUtil.CUSTOM_OBJECT);
        String value = domProcessor.serializeObjectToString(containerValue, root);

        return new RClobValue(value);

    }

    private RClobValue createClobValue(PrismPropertyValue propertyValue) {
        String value;
        Object object = propertyValue.getValue();
        if (object instanceof Element) {
            Element element = (Element) object;
            value = DOMUtil.serializeDOMToString(element);
        } else {
            value = object.toString();
        }

        return new RClobValue(value);
    }

    private <T> T extractValue(PrismPropertyValue value, Class<T> returnType) throws SchemaException {
        ItemDefinition definition = value.getParent().getDefinition();
        ValueType willBeSaveAs = getValueType(definition.getTypeName());

        Object object = value.getValue();
        if (object instanceof Element) {
            Element element = (Element) object;
            if (ValueType.STRING.equals(willBeSaveAs)) {
                return (T) element.getTextContent();
            } else {
                object = XmlTypeConverter.toJavaValue(element, definition.getTypeName());
            }
        }

        //check float/double to string
        if (object instanceof Float) {
            object = ((Float) object).toString();
        } else if (object instanceof Double) {
            object = ((Double) object).toString();
        }

        //check short/integer to long
        if (object instanceof Short) {
            object = ((Short) object).longValue();
        } else if (object instanceof Integer) {
            object = ((Integer) object).longValue();
        }

        //check gregorian calendar, xmlgregorian calendar to date
        if (object instanceof GregorianCalendar) {
            object = ((GregorianCalendar) object).getTime();
        } else if (object instanceof XMLGregorianCalendar) {
            object = XMLGregorianCalendarType.asDate(((XMLGregorianCalendar) object));
        }

        if (returnType.isAssignableFrom(object.getClass())) {
            return (T) object;
        }

        //todo raw types

        throw new IllegalStateException("Can't extract value for saving from prism property value\n" + value);
    }

    private ValueType getValueType(QName qname) {
        if (qname == null) {
            return ValueType.STRING;
        }
        ValueType type = TYPE_MAP.get(qname);
        if (type == null) {
            return ValueType.STRING;
        }
        return type;
    }

    void convertFromValue(RValue value, PrismContainerValue any) throws DtoTranslationException {
        Validate.notNull(value, "Value for converting must not be null.");
        Validate.notNull(any, "Parent prism container value must not be null.");

        try {
            Item item = any.findOrCreateItem(value.getName(), value.getValueType().getItemClass());
            if (item == null) {
                throw new DtoTranslationException("Couldn't create item for value '" + value.getName() + "'.");
            }

            addValueToItem(value, item);
        } catch (DtoTranslationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DtoTranslationException(ex.getMessage(), ex);
        }
    }

    private Element createElement(QName name) {
        if (document == null) {
            document = DOMUtil.getDocument();
        }

        return DOMUtil.createElement(document, name);
    }

    private void addValueToItem(RValue value, Item item) throws SchemaException {
        Object realValue = createRealValue(value, item.getDefinition());

        switch (value.getValueType()) {
            case REFERENCE:
                //todo implement
                // PrismReferenceValue referenceValue = new PrismReferenceValue();
                // item.add(referenceValue);
                throw new UnsupportedOperationException("Not implemented yet.");
            case PROPERTY:
                PrismPropertyValue propertyValue = new PrismPropertyValue(realValue, null, null);
                item.add(propertyValue);
                break;
            case OBJECT:
            case CONTAINER:
                //todo implement
                // PrismContainerValue containerValue = new PrismContainerValue();
                // item.add(containerValue);
                throw new UnsupportedOperationException("Not implemented yet.");
        }
    }

    private Object createRealValue(RValue rValue, ItemDefinition definition) throws SchemaException {
        if (rValue instanceof RClobValue) {
            RClobValue clob = (RClobValue) rValue;
            //todo maybe use prismcontext parser to get value from clob...
            return DOMUtil.parseDocument(clob.getValue()).getDocumentElement();
        }

        Object value = rValue.getValue();
        if (rValue instanceof RDateValue) {
            if (value instanceof Date) {
                return XMLGregorianCalendarType.asXMLGregorianCalendar((Date) value);
            }
        } else if (rValue instanceof RLongValue) {
            if (DOMUtil.XSD_LONG.equals(rValue.getType())) {
                return value;
            } else if (DOMUtil.XSD_INTEGER.equals(rValue.getType())) {
                return ((Long) value).intValue();
            } else if (DOMUtil.XSD_SHORT.equals(rValue.getType())) {
                return ((Long) value).shortValue();
            }
        } else if (rValue instanceof RStringValue) {
            if (DOMUtil.XSD_STRING.equals(rValue.getType())) {
                return value;
            } else if (DOMUtil.XSD_DOUBLE.equals(rValue.getType())) {
                return Double.parseDouble((String) value);
            } else if (DOMUtil.XSD_FLOAT.equals(rValue.getType())) {
                return Float.parseFloat((String) value);
            }
        }

        LOGGER.trace("Couln't create real value of type '{}' from '{}'",
                new Object[]{rValue.getType(), rValue.getValue()});

        throw new IllegalStateException("Can't create real value of type '" + rValue.getType()
                + "' from value saved in DB as '" + rValue.getClass().getSimpleName() + "'.");
    }
}
