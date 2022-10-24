package com.sooyeol.sceneviewuses

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import com.google.ar.core.Pose
import com.google.ar.sceneform.CameraNode
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.RenderableInstance
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.rendering.ViewSizer
import com.sooyeol.sceneviewuses.databinding.PhotoFrameLayoutBinding
import io.github.sceneview.math.Position
import io.github.sceneview.node.ViewNode
import io.github.sceneview.utils.FrameTime
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

open class PhotoFrameNode(
    val context: Context,
    lifecycle: Lifecycle,
    val listener: OnFrameListener,
    val size: Size
) : ViewNode() {


    var width: Int = -1
    var height: Int = -1
    private var leftTopParent: View? = null

    init {
        isSelectable = false
        loadView(context, lifecycle, R.layout.photo_frame_layout)
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    override fun onViewLoaded(renderableInstance: RenderableInstance, view: View) {
        super.onViewLoaded(renderableInstance, view)
        leftTopParent = view.findViewById(R.id.left_top_parent)
        val layoutParams = FrameLayout.LayoutParams(
            size.width / 2,
            size.height / 2
        )
        view.layoutParams = layoutParams
        renderableInstance.renderable.apply {
            //그림자를 없애준다.
            isShadowCaster = false
            isShadowReceiver = false
            renderPriority = 0
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

    var leftTopParentPoint
        get() = leftTopParent
        set(value) {
            leftTopParent = value
        }



//    //픽셀을 디피로 전환해주는 메서드
//    private fun convertPixelsToDp(px: Float): Int {
//        return (px / (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
//    }


}