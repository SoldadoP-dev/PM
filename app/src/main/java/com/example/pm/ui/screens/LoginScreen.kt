package com.example.pm.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.example.pm.ui.theme.NeonPink
import com.example.pm.ui.viewmodels.LoginViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    val emailError by viewModel.emailError.collectAsState()
    val passwordError by viewModel.passwordError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loginEvent.collectLatest { event ->
            when (event) {
                is LoginViewModel.LoginEvent.Success -> {
                    navController.navigate("main") { 
                        popUpTo("login") { inclusive = true } 
                    }
                }
                is LoginViewModel.LoginEvent.Error -> {
                    Toast.makeText(context, context.getString(event.messageRes), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepSpace).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(200.dp)
                .padding(8.dp),
            contentScale = ContentScale.Fit
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = NeonPurple)
        Text(stringResource(R.string.slogan), color = Color.Gray, modifier = Modifier.padding(bottom = 32.dp))

        OutlinedTextField(
            value = email, 
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            isError = emailError != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonPurple, 
                unfocusedBorderColor = Color.DarkGray, 
                focusedLabelColor = NeonPurple, 
                cursorColor = NeonPurple,
                errorBorderColor = Color.Red,
                errorLabelColor = Color.Red
            )
        )
        AnimatedVisibility(visible = emailError != null) {
            Text(
                text = emailError?.let { stringResource(it) } ?: "",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password, 
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            isError = passwordError != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonPurple, 
                unfocusedBorderColor = Color.DarkGray, 
                focusedLabelColor = NeonPurple, 
                cursorColor = NeonPurple,
                errorBorderColor = Color.Red,
                errorLabelColor = Color.Red
            )
        )
        AnimatedVisibility(visible = passwordError != null) {
            Text(
                text = passwordError?.let { stringResource(it) } ?: "",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
        ) {
            Text(stringResource(R.string.login), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        TextButton(onClick = { navController.navigate("register") }) {
            Text(stringResource(R.string.no_account), color = NeonPink)
        }
    }
}
