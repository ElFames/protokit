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
import grpcbin.DummyRequest
import grpcbin.GRPCBinClient

@Composable
@Preview
fun App() {
    val state by remember { mutableStateOf<UiState>(UiState.Loading) }
    var strResponse by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val client = GRPCBinClient(ProtoKitClient(
            provideGrpcTransport("https://grpcbin.test.k6.io")
        ))
        val response = client.dummyUnary(request = DummyRequest("hi mars"))
        println("++++++++++++++++++++++++")
        println(response.message)
    }

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