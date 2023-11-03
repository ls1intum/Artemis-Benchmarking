package de.tum.cit.ase.artemisModel;

public class Course extends DomainObject {

    private String title;
    private String shortName;

    public Course(String title, String shortName) {
        this.title = title;
        this.shortName = shortName;
    }

    public Course() {}

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }
}
