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
import com.fames.protokit.sdk.models.onFailure
import com.fames.protokit.sdk.models.onSuccess
import example.ExampleRequest
import example.ExampleServiceClient
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
            // Se instancian todas las clases necesarias para el ejemplo
            val client = ProtoClient("https://gref38b0c28f03.free.beeceptor.com")
            val service = ExampleServiceClient(client)
            val request = ExampleRequest("hi")
            val response = service.exampleMethod(request)
                .onSuccess {
                    println(it.message)
                    strResponse = it.message
                    state = UiState.Idle
                }.onFailure { error ->
                    println("""
                        Status: ${error.status}
                        Message: ${error.message}
                        Trailers: ${error.trailers.raw}
                        TrailersInfo: ${error.trailers.message} - ${error.trailers.status}
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
            Las clases y los metodos son autogenerados y se hace encapsulacion del error
            Solo hay que proveer un ProtoClient y la Request necesaria
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