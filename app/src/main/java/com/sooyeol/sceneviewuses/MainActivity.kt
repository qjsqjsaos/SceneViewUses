package com.sooyeol.sceneviewuses

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.isGone
import com.sooyeol.sceneviewuses.databinding.ActivityMainBinding
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.rotation
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArModelNode.Companion.DEFAULT_PLACEMENT_DISTANCE
import io.github.sceneview.ar.node.CursorNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.renderable.setCastShadows
import io.github.sceneview.renderable.setReceiveShadows
import io.github.sceneview.renderable.setScreenSpaceContactShadows
import io.github.sceneview.utils.getResourceUri

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    lateinit var modelNode: ArModelNode
    lateinit var cursorNode: CursorNode



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        modelNode = ArModelNode(
            context = this,
            lifecycle = lifecycle,
            modelFileLocation = "models/spiderbot.glb",
            scaleToUnits = 0.5f
        ).also {
            it.renderables.map { render ->
                render.setCastShadows(false)
                render.setReceiveShadows(false)
                render.setScreenSpaceContactShadows(false)
            }
//            it.applyPoseRotation = true
        }

        binding.sceneView.apply {
            //바닥에 무늬들을 제거한다.
            planeRenderer.isVisible = false

//            onArSessionFailed = { _: Exception ->
//                //카메라 권한이 거부되거나, Ar이 실패했을때
//                modelNode.centerModel(origin = Position(x = 0.0f, y = 0.0f, z = 0.0f))
//                modelNode.scaleModel(units = 1.0f)
//                binding.sceneView.addChild(modelNode)
//            }
        }
//
//        cursorNode = CursorNode(
//            context = this,
//            lifecycle = lifecycle
//        ).also {
//            it.
//        }

        binding.sceneView.addChild(modelNode)

    }

}