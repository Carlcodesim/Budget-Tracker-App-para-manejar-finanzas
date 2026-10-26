package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.RecurringExpensesScheduler
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecurringExpensesSchedulerTest {

    @Test
    fun testScheduleAllDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context)

        // Verificar que la configuración de WorkManager y AlarmManager se inicialice correctamente
        RecurringExpensesScheduler.scheduleAll(context)
        RecurringExpensesScheduler.scheduleDailyAlarm(context)
        RecurringExpensesScheduler.schedulePeriodicWork(context)
        RecurringExpensesScheduler.runImmediateCheck(context)
    }
}
