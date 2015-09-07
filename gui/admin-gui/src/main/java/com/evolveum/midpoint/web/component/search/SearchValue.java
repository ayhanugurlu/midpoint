package com.evolveum.midpoint.web.component.search;

import com.evolveum.midpoint.util.DisplayableValue;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @author Viliam Repan (lazyman)
 */
public class SearchValue<T extends Serializable> implements DisplayableValue<T>, Serializable {

    public static final String F_VALUE = "value";
    public static final String F_LABEL = "label";

    private T value;
    private String label;

    public SearchValue() {
        this(null, null);
    }

    public SearchValue(T value) {
        this.value = value;
    }

    public SearchValue(T value, String label) {
        this.label = label;
        this.value = value;
    }

    @Override
    public String getDescription() {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public String getLabel() {
        if (label == null && value != null) {
            return value.toString();
        }

        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setValue(T value) {
        this.value = value;

        if (value instanceof DisplayableValue) {
            DisplayableValue dv = (DisplayableValue) value;
            setLabel(dv.getLabel());
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("label", label)
                .append("value", value)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchValue<?> that = (SearchValue<?>) o;

        return !(value != null ? !value.equals(that.value) : that.value != null);

    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{value, label});
    }
}