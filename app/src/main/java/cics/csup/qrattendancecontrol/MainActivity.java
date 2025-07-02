package cics.csup.qrattendancecontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RadioGroup modeRadioGroup;
    private RadioButton radioTimeIn, radioTimeOut;
    private Button scanButton, historyButton;
    private TextView qrDataText, statusText, timeText, dateText;
    private EditText sectionEditText;

    private AttendanceDBHelper db;
    private SharedPreferences sharedPreferences;

    private static final String PREFS_NAME = "AttendancePrefs";
    private static final String KEY_SECTION = "last_section";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyWindowInsetPadding();

        modeRadioGroup = findViewById(R.id.modeRadioGroup);
        radioTimeIn = findViewById(R.id.radioTimeIn);
        radioTimeOut = findViewById(R.id.radioTimeOut);
        scanButton = findViewById(R.id.scanButton);
        historyButton = findViewById(R.id.historyButton);
        qrDataText = findViewById(R.id.qrDataText);
        statusText = findViewById(R.id.statusText);
        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);
        sectionEditText = findViewById(R.id.sectionEditText);

        db = new AttendanceDBHelper(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String lastSection = sharedPreferences.getString(KEY_SECTION, "");
        sectionEditText.setText(lastSection);

        historyButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        scanButton.setOnClickListener(v -> {
            hideKeyboard();

            int selectedId = modeRadioGroup.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(this, "Please select Time In or Time Out.", Toast.LENGTH_SHORT).show();
                return;
            }

            String section = sectionEditText.getText().toString().trim();
            if (section.isEmpty()) {
                Toast.makeText(this, "Please enter your section before scanning.", Toast.LENGTH_SHORT).show();
                return;
            }

            sharedPreferences.edit().putString(KEY_SECTION, section).apply();

            IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
            integrator.setPrompt("Scan QR Code");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(true);
            integrator.setCaptureActivity(QRScanActivity.class);
            integrator.initiateScan();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null && result.getContents() != null) {
            String qrContent = result.getContents().trim(); // Only name, no section
            String mode = radioTimeIn.isChecked() ? "in" : "out";
            String status = db.markAttendance(qrContent, mode); // Save only name

            String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            String currentDate = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(new Date());

            qrDataText.setText("QR Data: " + qrContent);
            statusText.setText("Status: " + (mode.equals("out") ? "Time-Out" : "Time-In"));
            timeText.setText("Time: " + currentTime);
            dateText.setText("Date: " + currentDate);

            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void applyWindowInsetPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(0, topInset, 0, bottomInset);
            return insets;
        });
    }
}
