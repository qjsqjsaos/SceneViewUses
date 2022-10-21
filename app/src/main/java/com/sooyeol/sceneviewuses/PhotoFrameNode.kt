package com.sooyeol.sceneviewuses

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import com.google.ar.sceneform.CameraNode
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.RenderableInstance
import com.google.ar.sceneform.rendering.ViewRenderable
import com.sooyeol.sceneviewuses.databinding.PhotoFrameLayoutBinding
import io.github.sceneview.math.Position
import io.github.sceneview.node.ViewNode
import io.github.sceneview.utils.FrameTime

open class PhotoFrameNode(
    val context: Context,
    lifecycle: Lifecycle,
    width: Int,
    height: Int,
    photoFrameBinding: PhotoFrameLayoutBinding,
    val listener: OnFrameListener
) : ViewNode() {

    override fun setRenderable(renderable: ViewRenderable?): RenderableInstance? {
        return super.setRenderable(renderable)
    }

    init {
        isSelectable = false
        position = Position(x = 0.0f, y = -0.75f, z = -2.0f)
        setScale(2f)

        val layoutParams = FrameLayout.LayoutParams(
            convertPixelsToDp(width.toFloat()),
            convertPixelsToDp(height.toFloat())
        )
        photoFrameBinding.photoFrameLayout.layoutParams = layoutParams

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            loadView(context, lifecycle, photoFrameBinding.photoFrameLayout.sourceLayoutResId)
        }
    }

    override fun onViewLoaded(renderableInstance: RenderableInstance, view: View) {
        super.onViewLoaded(renderableInstance, view)
        renderableInstance.renderable.apply {
            isShadowCaster = false
            isShadowReceiver = false
            renderPriority = 0
        }
    }

    override fun onFrame(frameTime: FrameTime) {
        super.onFrame(frameTime)
        listener.onFrame()
    }

    //픽셀을 디피로 전환해주는 메서드
    private fun convertPixelsToDp(px: Float): Int {
        return (px / (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }


}