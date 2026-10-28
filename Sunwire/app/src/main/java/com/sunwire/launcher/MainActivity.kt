package com.sunwire.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)        // bumps on resume so the app list refreshes
    private var homeSignal by mutableIntStateOf(0)  // bumps when Home is pressed
    private var active by mutableStateOf(false)     // true only while the home screen is in front

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Neo.load(this) // saved style, colours and spin speed, before anything is drawn
        setContent { SunwireTheme { LauncherRoot(tick, homeSignal, active) } }
    }

    override fun onResume() {
        super.onResume()
        tick++
        active = true
    }

    override fun onPause() {
        active = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal++
    }
}
