package com.nkusharora.smsforwarder

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nkusharora.smsforwarder.data.Prefs
import com.nkusharora.smsforwarder.databinding.ActivityMainBinding
import com.nkusharora.smsforwarder.service.ForwardingService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = Prefs.get(this)
        binding.destinationInput.setText(Prefs.destination(this).orEmpty())
        binding.enabledSwitch.isChecked = Prefs.isEnabled(this)
        binding.includeSenderSwitch.isChecked = Prefs.includeSender(this)
        binding.includeSimSwitch.isChecked = Prefs.includeSim(this)
        binding.blockedInput.setText(prefs.getString(Prefs.KEY_BLOCKED, ""))

        binding.saveButton.setOnClickListener { onSaveClicked() }
        binding.defaultAppButton.setOnClickListener { requestDefaultSmsRole() }
        binding.permissionsButton.setOnClickListener {
            permissionLauncher.launch(requiredPermissions().toTypedArray())
        }
        binding.batteryButton.setOnClickListener { requestBatteryExemption() }

        ForwardingService.start(this)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun onSaveClicked() {
        val dest = binding.destinationInput.text?.toString()?.trim().orEmpty()
        if (dest.isEmpty()) {
            Toast.makeText(this, "Destination number required", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.get(this).edit().apply {
            putString(Prefs.KEY_DEST, dest)
            putBoolean(Prefs.KEY_ENABLED, binding.enabledSwitch.isChecked)
            putBoolean(Prefs.KEY_INCLUDE_SENDER, binding.includeSenderSwitch.isChecked)
            putBoolean(Prefs.KEY_INCLUDE_SIM, binding.includeSimSwitch.isChecked)
            putString(Prefs.KEY_BLOCKED, binding.blockedInput.text?.toString()?.trim().orEmpty())
        }.apply()
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        ForwardingService.start(this)
        refresh()
    }

    @SuppressLint("SetTextI18n")
    private fun refresh() {
        val isDefault = Telephony.Sms.getDefaultSmsPackage(this) == packageName
        val perms = requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batteryOk = if (Build.VERSION.SDK_INT >= 23)
            pm.isIgnoringBatteryOptimizations(packageName) else true

        binding.statusText.text = buildString {
            append("Default SMS app: ").append(if (isDefault) "YES" else "no").append('\n')
            append("Permissions:     ").append(if (perms) "granted" else "MISSING").append('\n')
            append("Battery exempt:  ").append(if (batteryOk) "yes" else "no").append('\n')
            append("Destination:     ").append(Prefs.destination(this@MainActivity) ?: "<not set>").append('\n')
            append("Forwarding:      ").append(if (Prefs.isEnabled(this@MainActivity)) "ON" else "off")
        }

        binding.defaultAppButton.isEnabled = !isDefault
        binding.batteryButton.isEnabled = !batteryOk
        binding.permissionsButton.isEnabled = !perms
    }

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                Toast.makeText(this, "SMS role not available on this device", Toast.LENGTH_LONG).show()
                return
            }
            if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                Toast.makeText(this, "Already default SMS app", Toast.LENGTH_SHORT).show()
                return
            }
            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
            roleLauncher.launch(intent)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
