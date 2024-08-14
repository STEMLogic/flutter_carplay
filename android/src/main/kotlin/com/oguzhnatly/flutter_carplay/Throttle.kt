package com.oguzhnatly.flutter_carplay


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  A utility class for debouncing high-frequency events.
 *
 *  Throttling ensures that high-frequency events (like rapid button clicks) trigger functionality
 *  such as network requests or animations only once in a specified time interval, instead of for
 *  each and every event.
 *
 *  This is accomplished by delaying the action (specified as a lambda function) by a fixed
 *  interval. If a new event occurs within this interval, the current event is completed then only
 *  new event is created otherwise it will return immediately.
 *
 *  @property scope The [CoroutineScope] in which the throttle operations are launched.
 */
class Throttle(private val scope: CoroutineScope) {

    private var job: Job? = null


    /**
     * Delays the execution of the specified [action] by the [interval].
     *
     * If this method is called again before the [interval] is over, If a new event occurs within
     * this interval, the current event is completed then only new event is created otherwise
     * it will return immediately.
     *
     * @param interval The time in milliseconds to wait before executing the [action].
     * @param action The action to execute.
     */
    fun throttle(interval: Long, action: () -> Unit) {
        if (job?.isCompleted == false) return // Cancel the previous job if it hasn't completed yet.
        job = scope.launch {
            // launch a coroutine in the provided scope.
            action() // Execute the provided action.
            delay(interval) // Suspend the coroutine for the specified interval.
        }
    }
}
