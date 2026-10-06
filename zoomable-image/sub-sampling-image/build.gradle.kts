plugins {
  id("me.saket.android.library")
  id("me.saket.library.publishing")
  id("me.saket.kotlin.multiplatform")
  id("me.saket.android.test")
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.paparazzi)
}
android {
  namespace = "me.saket.telephoto.subsamplingimage"
  buildFeatures {
    buildConfig = true
  }
}
kotlin {
  sourceSets {
    commonMain.dependencies {
      api(projects.zoomable)
      api(libs.okio.core)
      api(compose.foundation)
      implementation(compose.uiUtil)
      implementation(libs.kotlinx.immutableCollections)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    // Every non-Android target renders with Skia (skiko): they share one decoder implementation.
    val skikoMain by creating {
      dependsOn(commonMain.get())
    }
    named("desktopMain") { dependsOn(skikoMain) }
    iosMain { dependsOn(skikoMain) }
    named("webMain") { dependsOn(skikoMain) }
    androidMain {
      dependencies {
        implementation(libs.androidx.ktx.core)
        implementation(libs.androidx.exif)
        implementation(libs.androidx.tracing)
      }
    }
    androidUnitTest {
      dependencies {
        implementation(libs.junit)
        implementation(libs.assertk)
        implementation(libs.testParamInjector)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.turbine)
        implementation(libs.robolectric)
      }
    }
    androidInstrumentedTest {
      dependencies {
        implementation(projects.testUtil)
      }
    }
  }
}
