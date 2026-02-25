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
import com.fames.protokit.sdk.models.Platform
import com.fames.protokit.sdk.models.onFailure
import com.fames.protokit.sdk.models.onSuccess
import com.fames.protokit.sdk.models.platform
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceContextServiceClient
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceInfoProto
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceRequest
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceSessionInfoProto
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

            val client = ProtoClient("https://motorcloud.atm.smarting.es:32132")
            val service = DeviceContextServiceClient(client)

            val request = DeviceRequest(DeviceInfoProto(
                deviceId = "gdgd".encodeToByteArray(),
                appInstanceId = "gdgd".encodeToByteArray(),
                operatingSystem = if (platform() == Platform.ANDROID) {
                    DeviceInfoProto.OperatingSystem.Android
                } else DeviceInfoProto.OperatingSystem.IOS,
                osVersion = "gdgd",
                deviceMaker = "gdgd",
                deviceModel = "gdgd",
                tamperStatus = "gdgd",
                deviceLocale = "gdgd",
                appLocale = "gdgd",
                fcmToken = "gdgd",
                sessionInfo = DeviceSessionInfoProto(
                    sessionTag = "mnm",
                    sessionId = "mnm",
                    systemVersionAccess = 1,
                    expiration = 55000L,
                    sessionRefresh = "mnm"
                ),
                versionCode = 3,
                appVersionName = "gdgd",
                apnsToken = "gdgd".encodeToByteArray()
            ))

            service.openSession(request)
                .onSuccess { deviceSession ->
                    strResponse = deviceSession.toString()
                }.onFailure { error ->
                    strResponse = error.toString()
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