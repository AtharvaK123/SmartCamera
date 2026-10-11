package com.hack.smartcam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hack.smartcam.data.Repo
import com.hack.smartcam.phone.Steps
import com.hack.smartcam.ui.AppRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        refreshSteps()
    }

    fun refreshSteps() {
        if (Repo.isGuest) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        lifecycleScope.launch {
            Steps.today(this@MainActivity)?.let { Repo.setSteps(it) }
        }
    }
}
