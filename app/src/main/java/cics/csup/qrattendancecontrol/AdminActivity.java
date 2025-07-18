package cics.csup.qrattendancecontrol;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AdminActivity extends AppCompatActivity {

    private final Set<String> sectionSet = new HashSet<>();
    private final List<AttendanceRecord> allRecords = new ArrayList<>();
    private FirebaseFirestore firestore;
    private LinearLayout sectionButtonContainer;
    private LinearLayout adminAttendanceContainer;
    private EditText searchNameEditText;
    private Button exportCsvButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private String currentSection = null;

    private final ActivityResultLauncher<Intent> createFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        writeCSVToUri(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String ADMIN_UID = "KCKVGF5sJ7TfGWKAl0fRJziE4Ja2"; // Replace with actual admin UID

        if (user == null || !user.getUid().equals(ADMIN_UID)) {
            Toast.makeText(this, "Access denied. Not an admin.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sectionButtonContainer = findViewById(R.id.sectionButtonContainer);
        adminAttendanceContainer = findViewById(R.id.adminDataContainer);
        searchNameEditText = findViewById(R.id.searchNameEditText);
        exportCsvButton = findViewById(R.id.exportCsvButton);
        swipeRefreshLayout = findViewById(R.id.adminSwipeRefreshLayout);
        firestore = FirebaseFirestore.getInstance();

        searchNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (currentSection != null) showRecordsForSection(currentSection);
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadAllRecords();
        });

        exportCsvButton.setOnClickListener(v -> createCSVFile());

        loadAllRecords();
    }

    private void loadAllRecords() {
        firestore.collection("attendance_records").get()
                .addOnSuccessListener(querySnapshots -> {
                    sectionSet.clear();
                    allRecords.clear();

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        String name = doc.getString("name");
                        String section = doc.getString("section");
                        String date = doc.getString("date");
                        String timeIn = doc.getString("time_in");
                        String timeOut = doc.getString("time_out");

                        if (name != null && section != null && date != null) {
                            AttendanceRecord record = new AttendanceRecord(
                                    0,
                                    name,
                                    date,
                                    timeIn != null ? timeIn : "-",
                                    timeOut != null ? timeOut : "-",
                                    section
                            );
                            record.setIdHash(doc.getId());
                            allRecords.add(record);
                            sectionSet.add(section);
                        }
                    }

                    swipeRefreshLayout.setRefreshing(false);
                    renderSectionButtons();
                })
                .addOnFailureListener(e -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Failed to load attendance records.", Toast.LENGTH_SHORT).show();
                });
    }

    private void renderSectionButtons() {
        sectionButtonContainer.removeAllViews();
        List<String> sortedSections = new ArrayList<>(sectionSet);
        java.util.Collections.sort(sortedSections);

        for (String section : sortedSections) {
            Button btn = new Button(this);
            btn.setText(section);
            btn.setAllCaps(false);
            btn.setTextColor(getColor(R.color.white));
            btn.setPadding(32, 20, 32, 20);
            btn.setBackgroundTintList(ColorStateList.valueOf(
                    section.equals(currentSection)
                            ? getColor(R.color.secondary_accent)
                            : getColor(R.color.primary_accent)));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 24, 0);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                currentSection = section;
                renderSectionButtons();
                showRecordsForSection(section);
            });

            sectionButtonContainer.addView(btn);
        }

        if (!sortedSections.isEmpty() && currentSection == null) {
            currentSection = sortedSections.get(0);
            renderSectionButtons();
            showRecordsForSection(currentSection);
        }
    }

    private void showRecordsForSection(String section) {
        adminAttendanceContainer.removeAllViews();
        String query = searchNameEditText.getText().toString().toLowerCase(Locale.getDefault());

        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord record : allRecords) {
            if (record.getSection().equals(section)) {
                if (query.isEmpty() || record.getName().toLowerCase().contains(query)) {
                    filtered.add(record);
                }
            }
        }

        if (filtered.isEmpty()) {
            showEmptyMessage(section);
            return;
        }

        for (AttendanceRecord record : filtered) {
            addRecordToUI(record);
        }
    }

    private void showEmptyMessage(String section) {
        TextView empty = new TextView(this);
        empty.setText("No records found for " + section);
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(getColor(R.color.text_secondary));
        empty.setPadding(16, 24, 16, 24);
        adminAttendanceContainer.addView(empty);
    }

    private void addRecordToUI(AttendanceRecord record) {
        TextView row = new TextView(this);
        row.setText(record.getName() + "\n"
                + "Date: " + record.getDate() + "\n"
                + "Time In: " + record.getTimeIn() + "\n"
                + "Time Out: " + record.getTimeOut());
        row.setTextSize(16);
        row.setTypeface(Typeface.MONOSPACE);
        row.setTextColor(getColor(R.color.text_primary));
        row.setBackgroundColor(getColor(R.color.card_background));
        row.setPadding(24, 24, 24, 24);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, 16);
        row.setLayoutParams(rowParams);

        row.setOnLongClickListener(v -> {
            showDeleteDialog(record);
            return true;
        });

        adminAttendanceContainer.addView(row);
    }

    private void showDeleteDialog(AttendanceRecord record) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Entry")
                .setMessage("Are you sure you want to delete this record?\n\n" + record.getName())
                .setPositiveButton("Delete", (dialog, which) -> confirmDeleteWithUndo(record))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteWithUndo(AttendanceRecord record) {
        allRecords.remove(record);
        showRecordsForSection(currentSection);

        Snackbar snackbar = Snackbar.make(
                findViewById(android.R.id.content),
                "Record deleted.",
                Snackbar.LENGTH_LONG
        );

        snackbar.setAction("Undo", v -> {
            allRecords.add(record);
            showRecordsForSection(currentSection);
        });

        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    firestore.collection("attendance_records").document(record.getIdHash())
                            .delete()
                            .addOnFailureListener(e ->
                                    Toast.makeText(AdminActivity.this, "Failed to delete from Firestore.", Toast.LENGTH_SHORT).show()
                            );
                }
            }
        });

        snackbar.show();
    }

    private void createCSVFile() {
        if (currentSection == null) {
            Toast.makeText(this, "No section selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String safeSection = currentSection.replaceAll("[^a-zA-Z0-9]", "_");
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String fileName = "BSIT_" + safeSection + "_" + currentDate + ".csv";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        createFileLauncher.launch(intent);
    }

    private void writeCSVToUri(Uri uri) {
        if (currentSection == null) {
            Toast.makeText(this, "No section selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder data = new StringBuilder();
        data.append("Name,Date,Time In,Time Out,Section\n");

        for (AttendanceRecord record : allRecords) {
            if (record.getSection().equals(currentSection)) {
                data.append("\"").append(record.getName()).append("\",")
                        .append("\"").append(record.getDate()).append("\",")
                        .append("\"").append(record.getTimeIn()).append("\",")
                        .append("\"").append(record.getTimeOut()).append("\",")
                        .append("\"").append(record.getSection()).append("\"\n");
            }
        }

        try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {

            writer.write(data.toString());
            writer.flush();
            Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}
