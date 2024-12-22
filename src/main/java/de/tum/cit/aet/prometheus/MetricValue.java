package de.tum.cit.aet.prometheus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class MetricValue {

    private ZonedDateTime dateTime;
    private double value;

    public MetricValue(double timestamp, double value) {
        this.value = value;
        long millis = (long) (timestamp * 1000);
        Instant instant = Instant.ofEpochMilli(millis);
        this.dateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("UTC"));
    }

    public ZonedDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(ZonedDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String toString() {
        return "Timestamp: " + dateTime + ", Value: " + value;
    }
}
