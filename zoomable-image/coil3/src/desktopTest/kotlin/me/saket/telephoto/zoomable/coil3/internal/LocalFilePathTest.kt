package me.saket.telephoto.zoomable.coil3.internal

import coil3.toUri
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalFilePathTest {
  @Test fun unix_path() {
    assertEquals("/tmp/a/large.png".toPath(), "/tmp/a/large.png".toUri(separator = "/").localFilePathOrNull())
  }

  /**
   * On Windows, Coil parses `K:\dev\large.png` with '\' as the separator and stores its path as
   * `K:/dev/large.png`. The file path must be rebuilt with the separator, like Coil's FileUriFetcher.
   */
  @Test fun windows_path() {
    val path = """K:\dev\large.png""".toUri(separator = "\\").localFilePathOrNull()
    assertEquals("""K:\dev\large.png""".toPath(), path)
    assertEquals(true, path?.isAbsolute)
  }

  @Test fun file_scheme() {
    assertEquals("/tmp/large.png".toPath(), "file:///tmp/large.png".toUri(separator = "/").localFilePathOrNull())
  }

  @Test fun remote_uri_is_not_a_file() {
    assertNull("https://example.com/large.png".toUri(separator = "/").localFilePathOrNull())
  }
}
