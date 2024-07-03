package com.oguzhnatly.flutter_carplay

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.oguzhnatly.flutter_carplay.managers.audio.FCPSpeaker
import com.oguzhnatly.flutter_carplay.models.map.FCPMapViewController
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


/**
 * A class representing an Android Auto session in the Flutter CarPlay plugin.
 *
 * This class handles the lifecycle of the Android Auto session, including starting and stopping the
 * Flutter engine, managing the screen manager, and handling method channel callbacks.
 *
 * @property flutterEngine The Flutter engine used to run the Flutter app.
 * @property isStartRequired A flag indicating whether the Flutter engine needs to be started.
 * @property screenManager The screen manager used to manage the screens in the Android Auto
 * session.
 */
class AndroidAutoSession : Session() {
    private var flutterEngine: FlutterEngine? = null

    /// A flag indicating whether the Flutter engine needs to be started.
    var isStartRequired = false

    /// The screen manager used to manage the screens in the Android Auto session.
    val screenManager = carContext.getCarService(ScreenManager::class.java)

    /// A debounce object for optimizing screen push.
    private val bouncer = Debounce(CoroutineScope(Dispatchers.Main))

    /**
     * Creates and returns a [Screen] for the given [Intent].
     *
     * This function adds a [DefaultLifecycleObserver] to the lifecycle of the session, which
     * handles the creation, start, pause, resume, stop, and destroy events of the session. The
     * observer initializes the [flutterEngine] if it is not already initialized, and updates the
     * [FCPConnectionStatus] accordingly in the [FlutterCarplayTemplateManager].
     *
     * The function returns a [Screen] created from the [FCPRootTemplate] in the
     * [FlutterCarplayPlugin] or a default [RootTemplate].
     *
     * @param intent The [Intent] used to create the screen.
     * @return A [Screen] representing the created screen.
     */
    override fun onCreateScreen(intent: Intent): Screen {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    Logger.log("onCreate")
                    flutterEngine = FlutterEngineCache.getInstance().get("SharedEngine")
                    if (flutterEngine == null) {
                        isStartRequired = true
                        flutterEngine = FlutterEngine(carContext.applicationContext)
                        FlutterEngineCache.getInstance().put("SharedEngine", flutterEngine)
                    }

                    super.onCreate(owner)
                }

                override fun onStart(owner: LifecycleOwner) {
                    Logger.log("onStart")
                    if (isStartRequired) {
                        flutterEngine!!.dartExecutor.executeDartEntrypoint(
                            DartExecutor.DartEntrypoint.createDefault()
                        )
                    }

                    super.onStart(owner)
                }

                override fun onPause(owner: LifecycleOwner) {
                    Logger.log("onPause")
                    FlutterCarplayTemplateManager.fcpConnectionStatus =
                        FCPConnectionTypes.BACKGROUND

                    super.onPause(owner)
                }

                override fun onResume(owner: LifecycleOwner) {
                    Logger.log("onResume")
                    FlutterCarplayTemplateManager.fcpConnectionStatus =
                        FCPConnectionTypes.CONNECTED

                    super.onResume(owner)
                }

                override fun onStop(owner: LifecycleOwner) {
                    Logger.log("onStop")
                    FlutterCarplayTemplateManager.fcpConnectionStatus =
                        FCPConnectionTypes.DISCONNECTED

                    super.onStop(owner)
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    Logger.log("onDestroy")

                    super.onDestroy(owner)
                }
            }
        )

        FCPSpeaker.initializeTTS()

        FlutterCarplayPlugin.rootViewController?.let {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(it)
        }

        return (FlutterCarplayPlugin.fcpRootTemplate ?: RootTemplate()).toScreen(carContext)
    }

    /** Called when the car configuration changes. */
    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        super.onCarConfigurationChanged(newConfiguration)

        (FlutterCarplayPlugin.rootViewController as? FCPMapViewController)?.onCarConfigurationChanged()
    }

    /**
     * Forces the update of the root template.
     *
     * This function checks if the root template exists in the FlutterCarplayPlugin. If it does, it
     * logs a message, pops to the root screen, pushes the new root template, and removes the old
     * root template. Finally, it returns a success result with a boolean value of true. If the root
     * template does not exist, it returns a success result with a boolean value of false.
     *
     * @param result the result object to send the success result to
     */
    fun forceUpdateRootTemplate(result: MethodChannel.Result? = null) {
        bouncer.debounce(interval = 500L) {
            FlutterCarplayPlugin.fcpRootTemplate?.let {
                if (it.elementId != (screenManager.top as? FCPScreen)?.fcpTemplate?.elementId) {
                    // Pop to root first inorder to remove all screens except root
                    screenManager.popToRoot()
                    // Push the new root template
                    screenManager.push(it.toScreen(carContext))
                    // Remove the old root template
                    screenManager.remove(screenManager.screenStack.last())
                } else {
                    it.onInvalidate()
                }
                result?.success(true)
            }
                ?: result?.success(false)
        }
    }

    /**
     * Pushes a given template onto the screen manager's stack if the navigation hierarchy does not
     * exceed 5 templates. If the hierarchy is exceeded, an error is logged and the result is sent
     * with an error message. Otherwise, the template is pushed onto the stack and the result is
     * sent with a success message.
     *
     * @param template the template to be pushed onto the stack
     * @param result the result object to send the result to
     */
    fun push(template: FCPTemplate, result: MethodChannel.Result? = null) {
        // Check if the navigation hierarchy exceeds 5 templates
        if (screenManager.stackSize >= 5) {
            Logger.log("Template navigation hierarchy exceeded")
            result?.error(
                "0",
                "Android Auto cannot have more than 5 templates on navigation hierarchy.",
                null
            )
            return
        }

        Logger.log("Push to $template.")
        screenManager.push(template.toScreen(carContext))
        result?.success(true)
    }

    /**
     * Pops the top template from the screen manager's stack and sends a success message to the
     * Flutter app.
     *
     * @param result The result object to send the result to.
     */
    fun pop(result: MethodChannel.Result? = null) {
        Logger.log("Pop Template.")
        screenManager.pop()
        result?.success(true)
    }

    /**
     * Pops the screen manager to its root template and sends a success message to the Flutter app.
     *
     * @param result The result object to send the result to.
     */
    fun popToRootTemplate(result: MethodChannel.Result? = null) {
        Logger.log("Pop to Root Template.")
        screenManager.popToRoot()
        result?.success(true)
    }

    /**
     * Closes the presented template and sends a success message to the Flutter app.
     *
     * @param result The result object to send the result to.
     */
    fun closePresent(result: MethodChannel.Result? = null) {
        Logger.log("Close the presented template")
        screenManager.pop()
        result?.success(true)
    }

    /**
     * Presents a template on the screen manager.
     *
     * @param template The template to present.
     * @param result The result object to send the result to.
     */
    fun presentTemplate(template: FCPTemplate, result: MethodChannel.Result? = null) {
        // Check if the navigation hierarchy exceeds 5 templates
        if (screenManager.stackSize >= 5) {
            Logger.log("Template navigation hierarchy exceeded")
            result?.error(
                "0",
                "Android Auto cannot have more than 5 templates on navigation hierarchy.",
                null
            )
            return
        }
        Logger.log("Present $template")
        screenManager.push(template.toScreen(carContext))
        result?.success(true)
    }
}
