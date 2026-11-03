package com.example.data.recovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseQuarantineTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        DatabaseAccessGate.resetForTests()
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        DatabaseAccessGate.resetForTests()
    }

    @Test
    fun deniedRoomAcquisitionDoesNotCreateDatabaseFile() {
        val databaseFile = context.getDatabasePath("budget_tracker_database")
        databaseFile.parentFile?.mkdirs()
        assertFalse(databaseFile.exists())

        assertThrows(IllegalStateException::class.java) {
            AppDatabase.getDatabase(
                context,
                CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
        }

        assertFalse("Room must not create the original database path while quarantined", databaseFile.exists())
    }
}
