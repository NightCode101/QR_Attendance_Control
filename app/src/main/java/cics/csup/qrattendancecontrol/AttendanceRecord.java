package cics.csup.qrattendancecontrol;

public class AttendanceRecord {
    public int id;
    public String name;
    public String date;
    public String timeIn;
    public String timeOut;

    public AttendanceRecord(int id, String name, String date, String timeIn, String timeOut) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.timeIn = timeIn;
        this.timeOut = timeOut;
    }
}
