package de.tum.cit.aet.prometheus;

import java.util.List;

public class Metric {

    private String name;
    private List<MetricValue> values;

    public Metric() {}

    public Metric(String name, List<MetricValue> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<MetricValue> getValues() {
        return values;
    }

    public void setValues(List<MetricValue> values) {
        this.values = values;
    }
}
