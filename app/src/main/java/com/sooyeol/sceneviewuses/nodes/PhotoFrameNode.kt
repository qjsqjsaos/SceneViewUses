package com.sooyeol.sceneviewuses.nodes

import android.content.Context
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import com.google.ar.sceneform.rendering.RenderableInstance
import com.google.ar.sceneform.rendering.ViewRenderable
import com.sooyeol.sceneviewuses.OnFrameListener
import com.sooyeol.sceneviewuses.R
import io.github.sceneview.node.ViewNode
import io.github.sceneview.utils.FrameTime

open class PhotoFrameNode(
    val context: Context,
    lifecycle: Lifecycle,
    val listener: OnFrameListener,
    val size: Size
) : ViewNode() {
    init {
        isSelectable = false
        loadView(context, lifecycle, R.layout.photo_frame_layout)
    }
    override fun onViewLoaded(renderableInstance: RenderableInstance, view: View) {
        super.onViewLoaded(renderableInstance, view)
        val layoutParams = FrameLayout.LayoutParams(
            size.width / 2,
            size.height / 2
        )
        view.layoutParams = layoutParams
        renderableInstance.renderable.apply {
            //그림자를 없애준다.
            isShadowCaster = false
            isShadowReceiver = false
        }
        renderable?.apply {
            //회전 축 가운데로 (수직, 수평 둘다)
            verticalAlignment = ViewRenderable.VerticalAlignment.CENTER
            horizontalAlignment = ViewRenderable.HorizontalAlignment.CENTER
            collisionShape = null
        }
    }

    override fun onFrame(frameTime: FrameTime) {
        super.onFrame(frameTime)
        listener.onFrame()
    }

}