package com.evolveum.midpoint.web.component.search;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Viliam Repan (lazyman)
 */
public class MoreDialogDto implements Serializable {

    public static final String F_NAME_FILTER = "nameFilter";
    public static final String F_PROPERTIES = "properties";

    private String nameFilter;

    private List<Property> properties;

    public String getNameFilter() {
        return nameFilter;
    }

    public void setNameFilter(String nameFilter) {
        this.nameFilter = nameFilter;
    }

    public List<Property> getProperties() {
        if (properties == null) {
            properties = new ArrayList<>();
        }
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }
}