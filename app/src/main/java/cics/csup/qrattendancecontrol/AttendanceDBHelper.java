package cics.csup.qrattendancecontrol;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

public class AttendanceDBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "attendance.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_NAME = "attendance";

    public AttendanceDBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "student_id TEXT, " +
                "date TEXT, " +
                "time_in TEXT, " +
                "time_out TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle future upgrades
    }

    // Called with scan mode: "in" or "out"
    public String markAttendance(String studentId, String mode) {
        SQLiteDatabase db = this.getWritableDatabase();

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String now = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        Cursor cursor = db.rawQuery(
                "SELECT * FROM " + TABLE_NAME + " WHERE student_id = ? AND date = ?",
                new String[]{studentId, today}
        );

        if (cursor.moveToFirst()) {
            String recordId = cursor.getString(cursor.getColumnIndexOrThrow("id"));
            String timeOut = cursor.getString(cursor.getColumnIndexOrThrow("time_out"));

            if (mode.equals("in")) {
                cursor.close();
                return "Duplicate Time-In. Entry ignored.";
            } else if (mode.equals("out")) {
                if (timeOut == null) {
                    ContentValues values = new ContentValues();
                    values.put("time_out", now);
                    db.update(TABLE_NAME, values, "id = ?", new String[]{recordId});
                    cursor.close();
                    return "Time-Out recorded for " + studentId;
                } else {
                    cursor.close();
                    return "Already timed out today.";
                }
            }
        } else {
            if (mode.equals("in")) {
                ContentValues values = new ContentValues();
                values.put("student_id", studentId);
                values.put("date", today);
                values.put("time_in", now);
                db.insert(TABLE_NAME, null, values);
                cursor.close();
                return "Time-In recorded for " + studentId;
            } else {
                cursor.close();
                return "Time-In not found. Cannot Time-Out.";
            }
        }

        cursor.close();
        return "Invalid operation.";
    }

    // Used in HistoryActivity to display entries
    public List<AttendanceRecord> getAttendanceRecords() {
        List<AttendanceRecord> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY date DESC, time_in DESC", null);

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("student_id"));
                String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                String timeIn = cursor.getString(cursor.getColumnIndexOrThrow("time_in"));
                String timeOut = cursor.getString(cursor.getColumnIndexOrThrow("time_out"));
                list.add(new AttendanceRecord(id, name, date, timeIn, timeOut));
            } while (cursor.moveToNext());
        }

        cursor.close();
        return list;
    }

    // Delete single attendance entry by ID
    public void deleteAttendanceById(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, "id = ?", new String[]{String.valueOf(id)});
    }

    // Clear all attendance records
    public void clearAllAttendance() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }
}
