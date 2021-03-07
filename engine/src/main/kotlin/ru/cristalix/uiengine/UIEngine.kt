package ru.cristalix.uiengine

import dev.xdark.clientapi.ClientApi
import dev.xdark.clientapi.event.Listener
import dev.xdark.clientapi.event.lifecycle.GameLoop
import dev.xdark.clientapi.event.render.GuiOverlayRender
import dev.xdark.clientapi.event.render.RenderPass
import dev.xdark.clientapi.event.window.WindowResize
import dev.xdark.clientapi.opengl.GLAllocation
import org.lwjgl.input.Mouse
import ru.cristalix.uiengine.element.*
import ru.cristalix.uiengine.utility.MouseButton
import ru.cristalix.uiengine.utility.V3
import java.nio.FloatBuffer

object UIEngine {

    val matrixBuffer: FloatBuffer = GLAllocation.createDirectFloatBuffer(16)

    /**
     * Instance of ClientApi.
     * You can reference that for any purposes.
     */
    lateinit var clientApi: ClientApi

    /**
     * This mod's listener.
     * Cristalix client is known to have some problems when registering mutiple listeners from a single mod.
     * Please, do not create your own listeners and stick to using this one.
     */
    lateinit var listener: Listener

    /**
     * Ingame HUD context that renders like chat, hotbar, etc.
     */
    val overlayContext: Context2D = Context2D(size = V3())

    /**
     * World contexts for stuff like holograms.
     * You can add your own Context3D here.
     * Please note that worldContexts is being cleared on respawns / world changes
     */
    val worldContexts: MutableList<Context3D> = ArrayList()

    internal var lastMouseState: BooleanArray = booleanArrayOf(false, false, false)

    /**
     * Main cristalix UI engine entrypoint.
     * It is recommended for every mod to call this as the first statement inside ModMain#load.
     */
    fun initialize(clientApi: ClientApi) {
        this.clientApi = clientApi
        val eventBus = clientApi.eventBus()
        this.listener = eventBus.createListener()
        eventBus.register(listener, GuiOverlayRender::class.java, { renderOverlay() }, 1)
        eventBus.register(listener, GameLoop::class.java, { gameLoop() }, 1)
        updateResolution()
        eventBus.register(listener, WindowResize::class.java, { updateResolution() }, 1)
        eventBus.register(listener, RenderPass::class.java, { renderWorld(it) }, 1)
    }

    private fun renderWorld(renderPass: RenderPass) {
        if (renderPass.pass != 2) return
        worldContexts.forEach { it.transformAndRender() }
    }

    private fun updateResolution() {
        val resolution = clientApi.resolution()
        overlayContext.size = V3(resolution.scaledWidth_double, resolution.scaledHeight_double)
    }

    private fun renderOverlay() {
        overlayContext.transformAndRender()
    }

    /**
     * Function that cleans up all of the event handlers registered by UI engine.
     * Please make sure to call that in your ModMain#unload
     */
    fun uninitialize() {
        clientApi.eventBus().unregisterAll(listener)
        GLAllocation.freeBuffer(matrixBuffer)
    }

    private fun findLastClickable(elements: Collection<AbstractElement>): AbstractElement? {
        var lastClickable: AbstractElement? = null
        for (element in elements) {
            // stdout.println(element.hovered + " " + element.passedHoverCulling + " " + (element.onClick != null))
            if (!element.passedHoverCulling) continue
            if (element.hovered && element.onClick != null) lastClickable = element
            if (element is RectangleElement) {
                lastClickable = findLastClickable(element.children) ?: lastClickable
            }
        }
        return lastClickable
    }

    /**
     * Convinient event handler registration.
     */
    // Avoid usage of forbidden Class class.
    @Suppress("NOTHING_TO_INLINE")
    inline fun <T> registerHandler(type: Class<T>, priority: Int = 1, noinline handler: T.() -> Unit) {
        clientApi.eventBus().register(listener, type, handler, priority)
    }

    private fun gameLoop() {
        for (button  in MouseButton.VALUES) {
            val idx = button.ordinal
            val oldState = lastMouseState[idx]
            val newState = Mouse.isButtonDown(idx)
            if (oldState != newState) {
                val lastClickable = findLastClickable(overlayContext.children)
                lastClickable?.onClick?.invoke(lastClickable, newState, button)
                lastMouseState[idx] = newState
            }
        }
    }

}
