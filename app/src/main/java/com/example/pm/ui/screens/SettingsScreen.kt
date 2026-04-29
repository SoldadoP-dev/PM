package com.example.pm.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    var showDeleteWarning by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordToDelete by remember { mutableStateOf("") }

    // Color rojo más suave para el botón de borrar (tipo granate/oscuro)
    val softRed = Color(0xFFB71C1C)

    // Diálogo de Advertencia
    if (showDeleteWarning) {
        AlertDialog(
            onDismissRequest = { showDeleteWarning = false },
            title = { Text(text = "¿Borrar cuenta?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Esta acción es irreversible. Se borrarán todos tus datos y publicaciones. ¿Deseas continuar?",
                    color = Color.LightGray,
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteWarning = false
                        showPasswordDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = softRed)
                ) {
                    Text("Continuar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWarning = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C1C1C),
            shape = RoundedCornerShape(28.dp)
        )
    }

    // Diálogo de Contraseña
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(text = "Confirmar contraseña", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Por seguridad, introduce tu contraseña para borrar la cuenta.", color = Color.LightGray, modifier = Modifier.padding(bottom = 16.dp))
                    TextField(
                        value = passwordToDelete,
                        onValueChange = { passwordToDelete = it },
                        placeholder = { Text("Contraseña", color = Color.Gray) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF262626),
                            unfocusedContainerColor = Color(0xFF262626),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        profileViewModel.deleteAccount(
                            passwordToDelete, 
                            onSuccess = {
                                navController.navigate("login") { popUpTo(0) }
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = softRed)
                ) {
                    Text("Borrar permanentemente", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1C1C1C),
            shape = RoundedCornerShape(28.dp)
        )
    }

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

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        profileViewModel.logout()
                        navController.navigate("login") { popUpTo(0) }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar sesión", color = Color.Red, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showDeleteWarning = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = softRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Borrar cuenta", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
