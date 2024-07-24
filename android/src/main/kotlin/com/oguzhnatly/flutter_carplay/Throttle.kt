package com.oguzhnatly.flutter_carplay


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  A utility class for debouncing high-frequency events.
 *
 *  Debouncing ensures that high-frequency events (like rapid button clicks) trigger functionality
 *  such as network requests or animations only once in a specified time interval, instead of for
 *  each and every event.
 *
 *  This is accomplished by delaying the action (specified as a lambda function) by a fixed
 *  interval. If a new event occurs within this interval, the previous event and its associated
 *  action get cancelled and a new delay period starts for the latest event.
 *
 *  @property scope The [CoroutineScope] in which the debounce operations are launched.
 */
class Throttle(private val scope: CoroutineScope) {
    /// A job represents a cancellable piece of work. In this case, the work is the debounced
    /// action that will be executed after a certain delay.
    /// The job is cancelled if a new event comes in before the delay period ends.
    private var job: Job? = null


    /**
     * Delays the execution of the specified [action] by the [interval].
     *
     * If this method is called again before the [interval] is over, the previous [action] is
     * cancelled and the delay resets for the latest invocation of the method.
     *
     * @param interval The time in milliseconds to wait before executing the [action].
     * @param action The action to execute after the delay.
     */
    fun throttle(interval: Long, action: () -> Unit) {
        if(job?.isCompleted == false) return // Cancel the previous job if it hasn't completed yet.
        job = scope.launch {
            // launch a coroutine in the provided scope.
            action() // Execute the provided action.
            delay(interval) // Suspend the coroutine for the specified interval.
        }
    }
}
