package com.fames.protokit

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.fames.protokit.runtime.ProtoKitClient
import example.ExampleRequest
import example.ExampleServiceClient
import grpcbin.DummyRequest
import grpcbin.GRPCBinClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@Composable
@Preview
fun App() {
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    var strResponse by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) {
        val client = ExampleServiceClient(ProtoKitClient(
            provideGrpcTransport("https://gref38b0c28f03.free.beeceptor.com")
        ))
        val response = client.exampleMethod(request = ExampleRequest("hi"))
        println("** Response: ${response.message}")
        state = UiState.Idle
    } }

    AnimatedContent(state) { s ->
        when(s) {
            UiState.Loading -> {
                CircularProgressIndicator()
            }
            is UiState.Error -> {
                Text((state as UiState.Error).message)
            }
            UiState.Idle -> {
                Text(strResponse)
            }
        }
    }

}

sealed class UiState {
    object Loading : UiState()
    data class Error(val message: String) : UiState()
    data object Idle : UiState()
}