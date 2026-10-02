package io.github.iannicholls89.wearmultitimer.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdaterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val installed get() = Updater.installedVersionCode(context)

    @org.junit.Before fun fresh() = Updater.reset()

    /** Exactly what the release workflow's printf writes. */
    private fun published(code: Long) =
        """{"versionCode":$code,"versionName":"0.3.3","apkUrl":"https://github.com/Ian-Nicholls89/WearMultiTimer/releases/download/v0.3.3/WearMultiTimer-v0.3.3.apk"}""" + "\n"

    @Test fun `reads what the release workflow publishes`() {
        val r = Updater.parse(published(7))
        assertEquals(7L, r.versionCode)
        assertEquals("0.3.3", r.versionName)
        assertEquals("https://github.com/Ian-Nicholls89/WearMultiTimer/releases/download/v0.3.3/WearMultiTimer-v0.3.3.apk", r.apkUrl)
    }

    @Test fun `a newer release is offered`() = runBlocking {
        Updater.check(context) { url ->
            assertEquals(Updater.LATEST_URL, url)
            published(installed + 1)
        }
        val s = Updater.status.value
        assertTrue("$s", s is Updater.Status.Available && s.release.versionCode == installed + 1)
    }

    @Test fun `the same or an older release is not`() = runBlocking {
        Updater.check(context, force = true) { published(installed) }
        assertEquals(Updater.Status.UpToDate, Updater.status.value)
        Updater.check(context, force = true) { published(installed - 1) }
        assertEquals(Updater.Status.UpToDate, Updater.status.value)
    }

    @Test fun `no connection or a bad file is reported, not thrown`() = runBlocking {
        Updater.check(context, force = true) { error("offline") }
        assertTrue(Updater.status.value is Updater.Status.Failed)
        Updater.check(context, force = true) { "<html>not json</html>" }
        assertTrue(Updater.status.value is Updater.Status.Failed)
    }

    @Test fun `it doesn't look again within half an hour unless asked`() = runBlocking {
        Updater.check(context, force = true) { published(installed) }
        var asked = false
        Updater.check(context) { asked = true; published(installed + 1) }
        assertEquals(false, asked)
        assertEquals(Updater.Status.UpToDate, Updater.status.value)
    }
}
