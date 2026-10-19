package com.charmjh7.baeminblack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.charmjh7.baeminblack.ui.*

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[BlackViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        model.handleIntent(intent)
        setContent { BlackTheme { BlackApp(model) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); model.handleIntent(intent, true) }
}
