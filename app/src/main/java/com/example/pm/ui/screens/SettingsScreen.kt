package com.example.pm.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.pm.R
import com.example.pm.ui.theme.DeepSpace
import com.example.pm.ui.theme.NeonPurple
import com.example.pm.ui.viewmodels.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    val user by profileViewModel.user.collectAsState()
    
    var isDarkMap by remember { mutableStateOf(sharedPrefs.getBoolean("dark_map", systemDark)) }
    var isEnglish by remember { mutableStateOf(sharedPrefs.getBoolean("is_english", false)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(DeepSpace).padding(padding).padding(16.dp)) {
            Text("Preferencias de la App", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
            
            // Map Theme
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isDarkMap) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = Color.White)
                Text(stringResource(R.string.dark_map), color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp), fontSize = 16.sp)
                Switch(
                    checked = isDarkMap,
                    onCheckedChange = {
                        isDarkMap = it
                        sharedPrefs.edit().putBoolean("dark_map", it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple, checkedTrackColor = NeonPurple.copy(alpha = 0.5f))
                )
            }
            
            HorizontalDivider(color = Color.DarkGray)
            
            // Language
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, null, tint = Color.White)
                Text(stringResource(R.string.english_language), color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp), fontSize = 16.sp)
                Switch(
                    checked = isEnglish,
                    onCheckedChange = {
                        isEnglish = it
                        sharedPrefs.edit().putBoolean("is_english", it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple, checkedTrackColor = NeonPurple.copy(alpha = 0.5f))
                )
            }

            HorizontalDivider(color = Color.DarkGray)
            
            // Ghost Mode (Libre para todos)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VisibilityOff, null, tint = Color.White)
                Text(stringResource(R.string.ghost_mode), color = Color.White, modifier = Modifier.weight(1f).padding(start = 16.dp), fontSize = 16.sp)
                Switch(
                    checked = user?.ghostMode ?: false,
                    onCheckedChange = {
                        profileViewModel.setGhostMode(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple, checkedTrackColor = NeonPurple.copy(alpha = 0.5f))
                )
            }
        }
    }
}
