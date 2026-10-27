package live.mehiz.mpvkt.ui.home

import android.app.Application
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ThumbnailCancellationTest {
  @Test fun leavingARowCancelsItsBlockedProviderRequest() = runBlocking {
    val entered = CountDownLatch(1)
    val cancelled = CountDownLatch(1)
    val request = launch(Dispatchers.IO) {
      cancellableThumbnailProvider { signal ->
        signal.setOnCancelListener { cancelled.countDown() }
        entered.countDown()
        // Model a provider waiting for native IO until Android's cancellation callback arrives.
        check(cancelled.await(5, TimeUnit.SECONDS))
      }
    }
    try {
      assertTrue(entered.await(3, TimeUnit.SECONDS))
      request.cancel()
      assertTrue(cancelled.await(3, TimeUnit.SECONDS))
      request.join()
      assertTrue(request.isCancelled)
    } finally { request.cancel() }
  }
}
