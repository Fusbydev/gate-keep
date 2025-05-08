package com.example.gatekeep;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    private TextInputEditText nameInput;
    private TableLayout trackerTable;
    private String pendingName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        FirebaseApp.initializeApp(this);  // Initialize Firebase
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("user");

        trackerTable = findViewById(R.id.trackerTable);
        fetchLogsFromFirebase();

        // Initialize NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
//            Toast.makeText(this, "NFC not supported", Toast.LENGTH_SHORT).show();
            Toast.makeText(this, "NFC not supported (Running in non-NFC mode)", Toast.LENGTH_SHORT).show();
//            finish();
            return;
        }

        nameInput = findViewById(R.id.inputName);
        trackerTable = findViewById(R.id.trackerTable);
        Button registerBtn = findViewById(R.id.btnRegister);

        registerBtn.setOnClickListener(v -> {
            if (nameInput.getText() != null && !nameInput.getText().toString().trim().isEmpty()) {
                pendingName = nameInput.getText().toString().trim();
                Toast.makeText(MainActivity.this, "Now scan the NFC tag to register", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Please enter a name or label", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE);
            IntentFilter[] filters = new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)};
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tag != null) {
                String uid = bytesToHex(tag.getId());

                if (!pendingName.isEmpty()) {
                    // Push to Firebase
                    DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("user");
                    String key = databaseReference.push().getKey();  // Generate a unique key
                    if (key != null) {
                        databaseReference.child(key).child("username").setValue(pendingName);
                        databaseReference.child(key).child("rfid_uid").setValue(uid);
                    }

                    Toast.makeText(this, "Registered: " + pendingName + " - " + uid, Toast.LENGTH_SHORT).show();
                    pendingName = "";
                    nameInput.setText("");
                } else {
                    Toast.makeText(this, "Please enter a name and click Register before scanning", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void addRowToTable(String name, String uid, String timestamp) {
        TableRow row = new TableRow(this);

        TextView nameText = new TextView(this);
        nameText.setText(name);
        nameText.setPadding(8, 8, 8, 8);

        TextView uidText = new TextView(this);
        uidText.setText(uid);
        uidText.setPadding(8, 8, 8, 8);

        TextView timeText = new TextView(this);
        timeText.setText(timestamp);
        timeText.setPadding(8, 8, 8, 8);

        row.addView(nameText);
        row.addView(uidText);
        row.addView(timeText);

        trackerTable.addView(row, 0);  // Newest on top
    }


    private void fetchLogsFromFirebase() {
        DatabaseReference logsRef = FirebaseDatabase.getInstance().getReference("logs");

        logsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                TableLayout trackerTable = findViewById(R.id.trackerTable);

                // Clear existing rows in the data table
                int childCount = trackerTable.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    trackerTable.removeViewAt(0); // Remove all data rows
                }

                // Add new rows below the header
                for (DataSnapshot logEntry : snapshot.getChildren()) {
                    String name = logEntry.child("username").getValue(String.class);
                    String uid = logEntry.child("rfid_uid").getValue(String.class);
                    String timestamp = logEntry.child("timestamp").getValue(String.class);

                    addRowToTable(timestamp, uid, name); // Add the data rows
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to fetch logs.", Toast.LENGTH_SHORT).show();
            }
        });
    }

}