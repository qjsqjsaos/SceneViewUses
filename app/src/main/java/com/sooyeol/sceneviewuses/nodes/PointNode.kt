package com.sooyeol.sceneviewuses.nodes

import android.content.Context
import android.util.Size
import android.view.View
import androidx.lifecycle.Lifecycle
import com.google.ar.sceneform.rendering.RenderableInstance
import com.google.ar.sceneform.rendering.ViewRenderable
import com.sooyeol.sceneviewuses.OnFrameListener
import com.sooyeol.sceneviewuses.R
import io.github.sceneview.node.ViewNode
import io.github.sceneview.utils.FrameTime

open class PointNode(
    context: Context,
    lifecycle: Lifecycle
) : ViewNode() {
    init {
        isSelectable = false
        loadView(context, lifecycle, R.layout.point)
    }
    override fun onViewLoaded(renderableInstance: RenderableInstance, view: View) {
        super.onViewLoaded(renderableInstance, view)
        renderableInstance.renderable.apply {
            //그림자를 없애준다.
            isShadowCaster = false
            isShadowReceiver = false
        }
        renderableInstance.renderable.apply {
            //그림자를 없애준다.
            isShadowCaster = false
            isShadowReceiver = false
        }
        renderable?.apply {
            //회전 축 가운데로 (수직, 수평 둘다)
            verticalAlignment = ViewRenderable.VerticalAlignment.BOTTOM
            horizontalAlignment = ViewRenderable.HorizontalAlignment.RIGHT
            collisionShape = null
        }
    }
}