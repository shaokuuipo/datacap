package io.edurt.datacap.schedule.local

import io.edurt.datacap.schedule.ScheduleRequest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class QuartzEndpointTest {
    private val name: String = "TestJob"
    private val group: String = "TestGroup"
    private val request = ScheduleRequest()

    @Before
    fun before() {
        request.name = name
        request.group = group
        request.expression = "*/10 * * * * ?"
        request.job = ExampleJob()
    }

    @Test
    fun testCreateJob() {
        assertTrue(QuartzEndpoint.createJob(request).successful)
    }

    @Test
    fun testRemoveJob() {
        QuartzEndpoint.createJob(request)
        assertTrue(QuartzEndpoint.removeJob(request).successful)
    }
}
