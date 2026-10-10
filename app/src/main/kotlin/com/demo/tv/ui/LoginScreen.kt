package com.demo.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import com.demo.xtream.Credentials
import com.demo.xtream.UrlNormalizer

@Composable
fun LoginScreen(initial: Credentials?, busy: Boolean, error: String?, onLogin: (Credentials) -> Unit) {
    var server by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var user by remember { mutableStateOf(initial?.username.orEmpty()) }
    var pass by remember { mutableStateOf(initial?.password.orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    fun submit() {
        // Allow pasting a full get.php / player_api.php link into the server box.
        val fromLink = UrlNormalizer.credentialsFromLink(server)
        val u = user.ifBlank { fromLink?.first.orEmpty() }
        val p = pass.ifBlank { fromLink?.second.orEmpty() }
        when {
            server.isBlank() -> localError = "Enter the server address"
            u.isBlank() || p.isBlank() -> localError = "Enter username and password"
            else -> try {
                localError = null
                onLogin(Credentials(UrlNormalizer.serverRoot(server), u.trim(), p.trim()))
            } catch (e: Exception) {
                localError = e.message
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Xtream TV", fontSize = 36.sp, color = Color.White)
            Text("Sign in with your Xtream Codes details", fontSize = 16.sp, color = TextDim)
            OutlinedTextField(
                server, { server = it }, label = { Text("Server (e.g. http://host:8080)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            )
            OutlinedTextField(
                user, { user = it }, label = { Text("Username") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            )
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); submit() }),
            )
            (localError ?: error)?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 16.sp) }
            if (busy) {
                CircularProgressIndicator(Modifier.padding(8.dp))
            } else {
                TvButton("Sign in", onClick = ::submit)
            }
        }
    }
}
