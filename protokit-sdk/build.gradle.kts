import org.jetbrains.dokka.gradle.DokkaTask
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.dokka)
    `maven-publish`
    signing
}

group = "es.nubaxsolutions"
version = "0.1.0"

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val dokkaOutputDir = "$buildDir/dokka"

tasks.getByName<DokkaTask>("dokkaHtml") {
    outputDirectory.set(file(dokkaOutputDir))
}

val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
    delete(dokkaOutputDir)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaOutputDir)
}

publishing {
    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = localProperties.getProperty("centralUsername")
                password = localProperties.getProperty("centralPassword")
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        artifact(javadocJar)
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
    val keyFile = rootProject.file("/Users/miguelangel.salazar/private.asc")
    val key = keyFile.readText()
    val secretPassword = localProperties.getProperty("signing.password")
    useInMemoryPgpKeys(key, secretPassword)
    sign(publishing.publications)
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
                implementation(libs.kotlinx.io.core)
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