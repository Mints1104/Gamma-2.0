package com.mints.projectgammatwo.helpers

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mints.projectgammatwo.R
import com.mints.projectgammatwo.services.OverlayService

/**
 * The Start/Stop Overlay button shared by the invasion and quest screens.
 *
 * Construct it as a property of the fragment: it registers a permission launcher, which is only
 * allowed before the fragment is created.
 */
class OverlayButtonController(private val fragment: Fragment, private val mode: String) {

    // The overlay starts whatever the answer: the notification is only needed for its Stop action
    // and quick way back into the app, and the overlay has its own close button.
    private val notificationPermission =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { startOverlay() }

    private var button: Button? = null

    /** Wires [button] up for the fragment's current view; call from onViewCreated. */
    fun bind(button: Button) {
        this.button = button
        button.setOnClickListener { onClick() }
        val viewLifecycleOwner = fragment.viewLifecycleOwner
        OverlayService.running.observe(viewLifecycleOwner) { updateLabel() }
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                // Don't hold on to the old view once it's gone.
                if (this@OverlayButtonController.button === button) this@OverlayButtonController.button = null
            }
        })
        updateLabel()
    }

    /** Re-reads the overlay permission, which may have changed in system settings; call from onResume. */
    fun updateLabel() {
        val button = button ?: return
        val context = fragment.context ?: return
        button.setText(
            when {
                OverlayService.running.value == true -> R.string.stop_overlay
                !Settings.canDrawOverlays(context) -> R.string.enable_overlay_permissions
                else -> R.string.enable_overlay
            }
        )
    }

    private fun onClick() {
        val context = fragment.requireContext()
        when {
            OverlayService.running.value == true -> OverlayServiceManager(context).stopOverlayService()
            !Settings.canDrawOverlays(context) -> showOverlayPermissionDialog()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED ->
                // After two refusals the system stops showing the prompt and answers straight away,
                // so this doesn't nag on every start.
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> startOverlay()
        }
    }

    private fun startOverlay() {
        val context = fragment.context ?: return
        OverlayServiceManager(context).startOverlayService(mode)
    }

    private fun showOverlayPermissionDialog() {
        val context = fragment.requireContext()
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_overlay_permission, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.notNowButton).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            fragment.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
            )
            dialog.dismiss()
        }
        dialog.show()
    }
}
