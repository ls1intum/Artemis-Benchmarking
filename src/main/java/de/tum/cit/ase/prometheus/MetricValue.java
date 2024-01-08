package de.tum.cit.ase.prometheus;

import java.util.Locale;

public class MetricValue {

    private double timestamp;
    private double value;

    public MetricValue() {}

    public MetricValue(double timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String toString() {
        return "Timestamp: " + String.format(Locale.US, "%.3f", timestamp) + ", Value: " + value;
    }
}
