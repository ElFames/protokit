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
import com.fames.protokit.runtime.ProtoKitClient
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
            val client = ExampleServiceClient(ProtoKitClient(provideGrpcTransport("https://gref38b0c28f03.free.beeceptor.com")))
            val response = client.exampleMethod(request = ExampleRequest("hi"))
            println("** Response: ${response.message}")
            state = UiState.Success
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
                UiState.Success -> {
                    Text(strResponse)
                }
            }
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    data object Success : UiState()
}