plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    `maven-publish`
    signing
}

group = "es.nubaxsolutions"
version = "0.1.0"

publishing {
    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://central.sonatype.com/api/v1/publisher/upload")
            credentials {
                username = project.findProperty("centralUsername")?.toString()
                password = project.findProperty("centralPassword")?.toString()
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("ProtoKit")
            description.set("A Kotlin Multiplatform gRPC client toolkit")
            url.set("https://github.com/ElFames/protokit")

            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }

            developers {
                developer {
                    id.set("M")
                    name.set("ElFames")
                    email.set("info@nubaxsolutions.es")
                }
            }

            scm {
                connection.set("scm:git:github.com/ElFames/protokit.git")
                developerConnection.set("scm:git:ssh://github.com/ElFames/protokit.git")
                url.set("https://github.com/ElFames/protokit")
            }
        }
    }

}

signing {
    val secretId = project.findProperty("signing.keyId")?.toString()
    val secretKey = project.findProperty("signing.secretKeyRingFile")?.toString()
    val secretPassword = project.findProperty("signing.password")?.toString()
    if (secretKey != null) {
        useInMemoryPgpKeys(
            secretId,
            secretKey,
            secretPassword
        )
        sign(publishing.publications)
    }
}


kotlin {

    jvm()

    androidLibrary {
        namespace = "com.fames.protokit.sdk"
        compileSdk = 36
        minSdk = 24
    }

    withSourcesJar()

    val xcfName = "protokit-sdk"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }

        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.ktor.client.okhttp)
            }
        }
    }

}