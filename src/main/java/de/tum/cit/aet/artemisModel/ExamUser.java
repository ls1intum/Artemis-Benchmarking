package de.tum.cit.aet.artemisModel;

public class ExamUser {

    private String actualRoom;
    private String actualSeat;
    private String plannedRoom;
    private String plannedSeat;
    private boolean didCheckImage = false;
    private boolean didCheckName = false;
    private boolean didCheckLogin = false;
    private boolean didCheckRegistrationNumber = false;
    private String signingImagePath;
    private String studentImagePath;
    private Exam exam;
    private User user;

    public String getActualRoom() {
        return actualRoom;
    }

    public void setActualRoom(String actualRoom) {
        this.actualRoom = actualRoom;
    }

    public String getActualSeat() {
        return actualSeat;
    }

    public void setActualSeat(String actualSeat) {
        this.actualSeat = actualSeat;
    }

    public String getPlannedRoom() {
        return plannedRoom;
    }

    public void setPlannedRoom(String plannedRoom) {
        this.plannedRoom = plannedRoom;
    }

    public String getPlannedSeat() {
        return plannedSeat;
    }

    public void setPlannedSeat(String plannedSeat) {
        this.plannedSeat = plannedSeat;
    }

    public boolean isDidCheckImage() {
        return didCheckImage;
    }

    public void setDidCheckImage(boolean didCheckImage) {
        this.didCheckImage = didCheckImage;
    }

    public boolean isDidCheckName() {
        return didCheckName;
    }

    public void setDidCheckName(boolean didCheckName) {
        this.didCheckName = didCheckName;
    }

    public boolean isDidCheckLogin() {
        return didCheckLogin;
    }

    public void setDidCheckLogin(boolean didCheckLogin) {
        this.didCheckLogin = didCheckLogin;
    }

    public boolean isDidCheckRegistrationNumber() {
        return didCheckRegistrationNumber;
    }

    public void setDidCheckRegistrationNumber(boolean didCheckRegistrationNumber) {
        this.didCheckRegistrationNumber = didCheckRegistrationNumber;
    }

    public String getSigningImagePath() {
        return signingImagePath;
    }

    public void setSigningImagePath(String signingImagePath) {
        this.signingImagePath = signingImagePath;
    }

    public String getStudentImagePath() {
        return studentImagePath;
    }

    public void setStudentImagePath(String studentImagePath) {
        this.studentImagePath = studentImagePath;
    }

    public Exam getExam() {
        return exam;
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
