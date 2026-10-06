plugins {
  id("me.saket.android.library")
  id("me.saket.library.publishing")
  id("me.saket.kotlin.multiplatform")
  id("me.saket.android.test")
  alias(libs.plugins.paparazzi)
}

android {
  namespace = "me.saket.telephoto.zoomable.image"
  testOptions {
    emulatorControl {
      enable = true
    }
  }
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.zoomable)
      api(projects.zoomableImage.subSamplingImage)
      api(compose.foundation)
    }
    androidUnitTest {
      dependencies {
        implementation(libs.junit)
        implementation(libs.assertk)
      }
    }
    androidInstrumentedTest {
      dependencies {
        implementation(projects.testUtil)
        implementation(libs.androidx.test.uiautomator)
        implementation(libs.espresso.device)
        implementation(libs.kotlinx.immutableCollections)
      }
    }
  }
}
