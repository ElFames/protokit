package com.fames.protokit

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fames.protokit.sdk.ProtoClient
import com.fames.protokit.sdk.models.getTrailers
import com.fames.protokit.sdk.models.onFailure
import com.fames.protokit.sdk.models.onSuccess
import com.fames.protokit.test.GetUserRequest
import com.fames.protokit.test.UserServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@Composable
@Preview
fun App() {

    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    var strResponse by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val client = ProtoClient("https://grb288f515738c.free.beeceptor.com")
            UserServiceClient(client).getUser(GetUserRequest(user_id = "exampleId"))
                .onSuccess { user ->
                    strResponse = "Repuesta Éxitosa.\nUsuario: ${user.display_name}\nRol: ${user.role.name.lowercase().replaceFirstChar { it.titlecase() }}"
                }.onFailure { error ->
                    strResponse = """
                        Error en la respuesta.
                        Status: ${error.status}
                        Mesage: ${error.message}
                        Trailers: ${error.trailers}
                    """.trimIndent()
                }
            println(strResponse)
            state = UiState.Idle
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(state) { s ->
            when(s) {
                UiState.Loading -> {
                    CircularProgressIndicator()
                }
                UiState.Idle -> {
                    Text(strResponse)
                }
            }
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    data object Idle : UiState()
}