plugins {
  id("me.saket.android.library")
  id("me.saket.library.publishing")
  id("me.saket.kotlin.multiplatform")
  id("me.saket.android.test")
  alias(libs.plugins.paparazzi)
}

android {
  namespace = "me.saket.telephoto.zoomable.image.coil3"
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.zoomableImage.core)
      api(libs.coil3.compose)
      implementation(projects.zoomableImage.subSamplingImage)
    }
    // Every non-Android target renders with Skia (skiko) and decodes images with Coil's Skia decoders.
    val skikoMain by creating {
      dependsOn(commonMain.get())
    }
    named("desktopMain") { dependsOn(skikoMain) }
    iosMain { dependsOn(skikoMain) }
    named("webMain") { dependsOn(skikoMain) }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    named("desktopTest") {
      dependencies {
        implementation(compose.desktop.currentOs)
        // Dispatchers.Main for desktop. Telephoto's RememberWorker runs on it, as do desktop apps.
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.kotlinx.coroutines.get()}")
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
      }
    }
    androidUnitTest {
      dependencies {
        implementation(libs.junit)
        implementation(libs.coil3.test)
        implementation(libs.compose.ui.material3)
        implementation(libs.testParamInjector)
      }
    }
    androidInstrumentedTest {
      dependencies {
        implementation(projects.testUtil)
        implementation(libs.turbine)
        implementation(libs.molecule.runtime)
        implementation(libs.okhttp.mockWebServer)
        implementation(libs.coil3.gif)
        implementation(libs.coil3.svg)
        implementation(libs.coil3.test)
        implementation(libs.coil3.okhttp)
        implementation(libs.okio.fakeFileSystem)
        implementation(libs.modernstorage)
      }
    }
  }
}
