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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import protos.GetUserRequest
import protos.UserServiceClient

@Composable
@Preview
fun App() {

    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    var strResponse by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Hay que instanciar un ProtoClient con las defaults configs que
            val client = ProtoClient("https://gre71378c17a79.free.beeceptor.com")

            // Instanciar servicio autogenerado
            val service = UserServiceClient(client)

            // Llamar al metodo del servicio con la request requerida
            val response = service.getUserProfile(GetUserRequest(id = 1))

            response.onSuccess { user ->
                println("""
                       User: $user
                       Trailers: ${response.getTrailers().raw}
                   """.trimIndent())
                strResponse = user.toString()
                state = UiState.Idle
            }.onFailure { error ->
                println("""
                    Error
                       Status: ${error.status}
                       Message: ${error.message}
                       Trailers: ${response.getTrailers().raw}
                   """.trimIndent())
                strResponse = error.message ?: error.status.name
                state = UiState.Idle
            }

            /*
            Ejemplo de uso:
            class ExampleApiImpl(private val client: ProtoClient) {
                fun fetch(request: ExampleRequest): Response<ExampleResponse> {
                    return ExampleServiceClient(client).exampleMethod(request).map { it.toDomain() }
                }
            }
            */

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