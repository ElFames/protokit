package com.fames.protokit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import com.fames.protokit.sdk.ProtoClient
import com.fames.protokit.sdk.models.Platform
import com.fames.protokit.sdk.models.platform
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceContextServiceClient
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceInfoProto
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceRequest
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceSessionInfoProto
import es.smarting.motorcloud.apis.baseremoteapi.grpc.services.DeviceVerificationRequest

@Composable
@Preview
fun App() {
    LaunchedEffect(Unit) {
        val client = ProtoClient("")
        val service = DeviceContextServiceClient(client)
        val response = service.openSession(deviceRequestTest)
    }
}

val deviceInfoProtoTest = DeviceInfoProto(
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
)
val deviceRequestTest = DeviceRequest(deviceInfoProtoTest)
val deviceVerificationRequestTest = DeviceVerificationRequest(deviceInfoProtoTest, listOf())
