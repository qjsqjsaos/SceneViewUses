package com.sooyeol.sceneviewuses

import android.graphics.Insets
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Size
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.CameraNode
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.sooyeol.sceneviewuses.databinding.ActivityMainBinding
import com.sooyeol.sceneviewuses.databinding.PhotoFrameLayoutBinding
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.CursorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.lookAt
import io.github.sceneview.math.toFloat3


class MainActivity : AppCompatActivity(), OnFrameListener, SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var photoFrameBinding: PhotoFrameLayoutBinding

    lateinit var modelNode: ArModelNode
    lateinit var cursorNode: CursorNode

    private var frameRenderable: ViewRenderable? = null

    var isTest = false

    private var photoFrameNode: PhotoFrameNode? = null



    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photoFrameBinding = PhotoFrameLayoutBinding.inflate(layoutInflater)

        //센서매니저를 초기화 해주고, 실기기 디바이스의 회전값을 얻기 위해
        //가속도계 센서 유형과 자기장 센서 유형을 가져온다.
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
        accelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
//
//        //포토프레임 레이아웃 바인딩하기
//        photoFrameBinding = PhotoFrameLayoutBinding.inflate(layoutInflater)

//        val node = ViewNode()
//        node.loadView(
//            context = this,
//            lifecycle = lifecycle,
//            layoutResId = R.layout.photo_frame_layout,
//            onLoaded = { instance, view ->
//
//                val test = ViewNode(renderableInstance = instance)
//                binding.sceneView.addChild(test)
//            }
//        )



//        modelNode = ArModelNode(
//            context = this,
//            lifecycle = lifecycle,
//            modelFileLocation = "models/frame.glb",
//            scaleToUnits = 0.5f
//        )

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
//            lifecycle = lifecycle,
//            modelFileLocation = "sceneview/models/frame.glb",
//            scaleToUnits = 0.1f,
//            centerOrigin = Float3(0.0f, 0.0f, 0.0f),
//        ).also {
//            it.placementMode = PlacementMode.PLANE_HORIZONTAL
//            it.isScaleEditable = false
//            it.minEditableScale = 1.0f
//            it.maxEditableScale = 1.0f
//        }
//
//
//        lifecycleScope.launch {
//            while (true) {
//                //모델노드와 카메라가 같은 회전값 가지게 하기
//                val cameraRotation = binding.sceneView.cameraNode.rotation
//                val x = cameraRotation.x
//                val y = cameraRotation.y
//                val z = cameraRotation.z
//
//                cursorNode.rotation = Rotation(x = x, y = y + 30, z = z - 10)
//
////                cursorNode.parent =
//                delay(500)
//            }
//        }
//        binding.sceneView.focusMode = Config.FocusMode.AUTO
//        binding.sceneView.addChild(cursorNode)

        val deviceSize = getDeviceSize()

        //디바이스 정보
        val width = deviceSize.width
        val height = deviceSize.height

        photoFrameNode = PhotoFrameNode(
            context = this,
            lifecycle = lifecycle,
            width = width,
            height = height,
            photoFrameBinding = photoFrameBinding,
            listener = this
        )


        binding.sceneView.addChild(photoFrameNode!!)
    }


    //노드의 각도 설정
    //이각도는 디바이스의 위치 따라 변화하는 각도 값이다.
    //세로일때
    private var nodeChangePortDegree = 0f
    //가로일때
    private var nodeChangeLandDegree = 0f

    override fun onFrame() {
        //노드가 있다면
        if(photoFrameNode != null) {
            photoFrameNode?.apply {
                val camera = binding.sceneView.cameraNode
                val ray: Ray?

                quaternion = if(isLandScape) {
                    //디바이스가 가로일때
                    dev.romainguy.kotlin.math.Quaternion.fromAxisAngle(
                        Vector3(0f, 1f, 0f).toFloat3(),
                        nodeChangeLandDegree
                    )
                } else {
                    //디바이스가 세로일때
                    dev.romainguy.kotlin.math.Quaternion.fromAxisAngle(
                        Vector3(1f, 0f, 0f).toFloat3(),
                        nodeChangePortDegree
                    )
                }


                // 디바이스 화면 기준으로 노드를 생성할 위치를 가져온다.
                val screenPoint = getScreenPoint()

                //화면을 마주보게 만들어주는 screenPointToRay를 사용하여, x y 값을 넣어준다.
                ray = camera.screenPointToRay(
                    screenPoint.x, screenPoint.y
                )

                parent = camera

                // ray에서 얼마나 떨어져 있는지 설정한다.
                worldPosition = Position(ray?.getPoint(0.9f)?.toFloat3()!!)

            }
        }
    }

    private fun getScreenPoint(widthRatio: Float = 2.0f, heightRatio: Float = 2.0f): Vector3 {
        val vw = findViewById<View>(android.R.id.content)
        return Vector3(vw.width / widthRatio, vw.height / heightRatio, 0f)
    }



    //디바이스의 해상도 사이즈를 가져와 준다.
    @RequiresApi(Build.VERSION_CODES.R)
    private fun getDeviceSize(): Size {
        val metrics = windowManager.currentWindowMetrics
        val windowInsets = metrics.windowInsets
        val insets: Insets = windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.navigationBars()
                    or WindowInsets.Type.displayCutout()
        )

        val insetsWidth: Int = insets.right + insets.left
        val insetsHeight: Int = insets.top + insets.bottom
        val bounds: Rect = metrics.bounds
        val legacySize = Size(
            bounds.width() - insetsWidth,
            bounds.height() - insetsHeight
        )
        return legacySize
    }

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var mSensorManager: SensorManager? = null
    private var mGravity: FloatArray? = null
    private var mGeomagnetic: FloatArray? = null
    private var isLandScape = false

    //각도를 계속 갱신해준다.
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) mGravity = event.values
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) mGeomagnetic = event.values
        if (mGravity != null && mGeomagnetic != null) {
            val r = FloatArray(9)
            val l = FloatArray(9)
            val success = SensorManager.getRotationMatrix(r, l, mGravity, mGeomagnetic)
            if (success) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                //방위각 x값 얻기
                val azimuthX = orientation[1].toDouble()
                //경사각 z값 얻기
//                val pitchZ = orientation[0].toDouble()
                //롤각 y값 얻기
                val rollY = orientation[2].toDouble()

                //실제 디바이스가 portrait인지 landscape인지 구별해주는 코드이다.
                if (event?.sensor == accelerometer) {
                    if(kotlin.math.abs(event?.values!![1]) > kotlin.math.abs(event.values[0])) {
                        //Mainly portrait
                        if (event.values[1] > 1) {
                            //Portrait
                        } else if (event.values[1] < -1) { //Inverse portrait
                        }
                        //방위각을 각도로 변환하고,
                        //해당 각도에 x값을 넣는다.
                        nodeChangePortDegree = Math.toDegrees(azimuthX).toFloat()
                        isLandScape = false
                    }else{
                        //Mainly landscape
                        if (event.values[0] > 1) {
                            //Landscape - right side up
                        } else if (event.values[0] < -1) {
                            //Landscape - left side up
                        }
                        //롤각을 각도로 변환하고,
                        //해당 각도에 y값을 넣는다.
                        nodeChangeLandDegree = -Math.toDegrees(rollY).toFloat()
                        isLandScape = true
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        mSensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        mSensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager?.unregisterListener(this)
    }

}