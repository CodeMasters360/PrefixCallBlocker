package com.example.callblocker;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 100;
    private static final int REQ_ROLE = 101;

    private Switch switchEnabled;
    private TextView statusText;
    private TextView logText;
    private EditText editRule;
    private LinearLayout rulesContainer;
    private Button btnRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchEnabled = findViewById(R.id.switch_enabled);
        statusText = findViewById(R.id.status_text);
        logText = findViewById(R.id.log_text);
        editRule = findViewById(R.id.edit_rule);
        rulesContainer = findViewById(R.id.rules_container);
        btnRole = findViewById(R.id.btn_role);

        switchEnabled.setChecked(RuleStore.isEnabled(this));
        switchEnabled.setOnCheckedChangeListener((button, checked) -> RuleStore.setEnabled(this, checked));

        findViewById(R.id.btn_permissions).setOnClickListener(v -> requestMissingPermissions(true));
        findViewById(R.id.btn_add).setOnClickListener(v -> addRuleFromInput());
        findViewById(R.id.btn_clear_log).setOnClickListener(v -> {
            RuleStore.clearLog(this);
            refresh();
        });

        btnRole.setVisibility(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? View.VISIBLE : View.GONE);
        btnRole.setOnClickListener(v -> requestScreeningRole());

        if (savedInstanceState == null) requestMissingPermissions(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---------------------------------------------------------------- permissions

    private List<String> requiredPermissions() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.READ_PHONE_STATE);
        list.add(Manifest.permission.READ_CALL_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            list.add(Manifest.permission.ANSWER_PHONE_CALLS);
        } else {
            list.add(Manifest.permission.CALL_PHONE);
        }
        return list;
    }

    private List<String> missingPermissions() {
        List<String> missing = new ArrayList<>();
        for (String p : requiredPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        return missing;
    }

    private void requestMissingPermissions(boolean fromButton) {
        List<String> missing = missingPermissions();
        if (missing.isEmpty()) {
            if (fromButton) toast("All permissions are already granted.");
            return;
        }
        requestPermissions(missing.toArray(new String[0]), REQ_PERMISSIONS);
    }

    // ---------------------------------------------------------------- Android 10 role

    private boolean isScreeningRoleHeld() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        return rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private void requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            toast("Call screening role is not available on this device.");
            return;
        }
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            toast("Already set as the call screening app.");
            return;
        }
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
    }

    // ---------------------------------------------------------------- patterns

    private void addRuleFromInput() {
        String text = editRule.getText().toString().trim();
        if (RuleStore.addRule(this, text)) {
            editRule.setText("");
            refresh();
        } else {
            toast("Enter a pattern with at least one digit (not already in the list).");
        }
    }

    // ---------------------------------------------------------------- UI refresh

    private void refresh() {
        // Status
        StringBuilder status = new StringBuilder();
        List<String> missing = missingPermissions();
        status.append("Permissions: ").append(missing.isEmpty() ? "OK" : "missing " + missing.size()).append('\n');
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            status.append("Call screening role: ").append(isScreeningRoleHeld() ? "active" : "not set").append('\n');
        }
        status.append("Android ").append(Build.VERSION.RELEASE);
        statusText.setText(status.toString());

        // Patterns
        rulesContainer.removeAllViews();
        List<String> rules = RuleStore.getRules(this);
        if (rules.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No patterns yet.");
            rulesContainer.addView(empty);
        }
        for (final String rule : rules) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = new TextView(this);
            label.setText(rule);
            label.setTextSize(18);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button delete = new Button(this);
            delete.setText("Delete");
            delete.setOnClickListener(v -> {
                RuleStore.removeRule(this, rule);
                refresh();
            });
            row.addView(delete, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            rulesContainer.addView(row);
        }

        // Log
        List<String> log = RuleStore.getLog(this);
        if (log.isEmpty()) {
            logText.setText("Nothing blocked yet.");
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            StringBuilder sb = new StringBuilder();
            for (String entry : log) {
                int idx = entry.indexOf('|');
                try {
                    long time = Long.parseLong(entry.substring(0, idx));
                    sb.append(fmt.format(new Date(time))).append("  ").append(entry.substring(idx + 1));
                } catch (Exception e) {
                    sb.append(entry);
                }
                sb.append('\n');
            }
            logText.setText(sb.toString().trim());
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
