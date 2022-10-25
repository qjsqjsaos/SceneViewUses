package com.sooyeol.sceneviewuses

import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Config
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Vector3
import com.sooyeol.sceneviewuses.databinding.ActivityMainBinding
import com.sooyeol.sceneviewuses.nodes.PhotoFrameNode
import com.sooyeol.sceneviewuses.nodes.PointNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.math.toVector3


class MainActivity : AppCompatActivity(), OnFrameListener, SensorEventListener {

    private lateinit var binding: ActivityMainBinding

    private var photoFrameNode: PhotoFrameNode? = null

    private var leftTopNode: PointNode? = null
    private var rightTopNode: PointNode? = null
    private var leftDownNode: PointNode? = null
    private var rightDownNode: PointNode? = null

    //화면 표시된 ScreenPoint
    private var leftTopPoint: Point? = null
    private var rightTopPoint: Point? = null
    private var leftDownPoint: Point? = null
    private var rightDownPoint: Point? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //센서매니저를 초기화 해주고, 실기기 디바이스의 회전값을 얻기 위해
        //가속도계 센서 유형과 자기장 센서 유형을 가져온다.
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
        accelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        binding.btn.setOnClickListener {
            perspectiveImage()
        }
    }

    //이미지를 perspective 변환해준다.
    private fun perspectiveImage() {

        //스크린상의 포인트를 담을 배열들이다.
        val leftTopPointArr = IntArray(2)
        val rightTopPointArr = IntArray(2)
        val leftDownPointArr = IntArray(2)
        val rightDownPointArr = IntArray(2)

        //각 노드들의 스크린상의 포인트를 가져와서 인트 배열에 넣어준다.
        binding.let {
            it.leftTopPoint.getLocationOnScreen(leftTopPointArr)
            it.rightTopPoint.getLocationOnScreen(rightTopPointArr)
            it.leftDownPoint.getLocationOnScreen(leftDownPointArr)
            it.rightDownPoint.getLocationOnScreen(rightDownPointArr)
        }

        //포인트 x, y 값(Point) 변수에 넣어주기 넣어주기
        leftTopPoint = Point(leftTopPointArr[0], leftTopPointArr[1])
        rightTopPoint = Point(rightTopPointArr[0], rightTopPointArr[1])
        leftDownPoint = Point(leftDownPointArr[0], leftDownPointArr[1])
        rightDownPoint = Point(rightDownPointArr[0], rightDownPointArr[1])

        val dw = 500.0
        val dh = dw * 600.0 / 388.0  // 명함 사이즈

        val srcQuad = MatOfPoint2f(
            leftTopPoint,
            rightTopPoint,
            leftDownPoint,
            rightDownPoint
        )

        val dstQuad = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(0.0, dh - 1),
            Point(dw - 1, dh - 1),
            Point(dw - 1, 0.0)
        )

        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
        val newDst = Mat()
        Imgproc.warpPerspective(src, newDst, perspectiveTransform, Size(dw, dh))
        dst = newDst
    }


    //이 시점이 바인디 sceneview의 사이즈를 얻어올 수 있는 생명주기 타이밍이다.
    //이 때 addChild를 해준다.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        photoFrameNode = PhotoFrameNode(
            context = this,
            lifecycle = lifecycle,
            listener = this,
            size = Size(binding.sceneView.width, binding.sceneView.height)
        )

        leftTopNode = PointNode(
            context = this,
            lifecycle = lifecycle
        )

        rightTopNode = PointNode(
            context = this,
            lifecycle = lifecycle
        )

        leftDownNode = PointNode(
            context = this,
            lifecycle = lifecycle
        )

        rightDownNode = PointNode(
            context = this,
            lifecycle = lifecycle
        )

        binding.sceneView.apply {
            planeRenderer.isVisible = false
            planeRenderer.isShadowReceiver = false
            planeRenderer.isEnabled = false
            focusMode = Config.FocusMode.AUTO
            addChild(photoFrameNode!!)
        }
    }



    //노드의 각도 설정
    //이각도는 디바이스의 위치 따라 변화하는 각도 값이다.
    //세로일때
    private var nodeChangePortDegree = 0f

    //가로일때
    private var nodeChangeLandDegree = 0f

    override fun onFrame() {
        val camera = binding.sceneView.cameraNode
        //노드가 있다면
        photoFrameNode?.apply {
            val ray: Ray?
            quaternion = if (isLandScape) {
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

            // ray에서 얼마나 떨어져 있는지 설정한다.
            worldPosition = Position(ray?.getPoint(1.2f)?.toFloat3()!!)

            // parentNode는 camera로 한다.
            parent = camera
        }

        //점 노드 찍기

        //왼쪽 위 점
        setPointNode(
            pointNode = leftTopNode,
            pos = Position(x = -0.00076f, y = 0.000745f, z = 0.013f),
            movingPoint = binding.leftTopPoint
        )

        //오른쪽 위 점
        setPointNode(
            pointNode = rightTopNode,
            pos = Position(x = 0.000724f, y = 0.000756f, z = 0.001f),
            movingPoint = binding.rightTopPoint
        )

        //왼쪽 아래 점
        setPointNode(
            pointNode = leftDownNode,
            pos = Position(x = -0.00075f, y = -0.000725f, z = 0.009f),
            movingPoint = binding.leftDownPoint
        )

        //오른쪽 아래 점
        setPointNode(
            pointNode = rightDownNode,
            pos = Position(x = 0.00072f, y = -0.000735f, z = -0.005f),
            movingPoint = binding.rightDownPoint
        )
    }

    //포인트 노드 부모 노드안에서 원하는 위치에 렌더 시키기
    private fun setPointNode(
        pointNode: PointNode?,
        parentNode: PhotoFrameNode? = photoFrameNode,
        pos: Position,
        movingPoint: View
    ) {
        //노드 위치 설정 및 부모 노드 설정
        pointNode?.apply {
            worldPosition = parentNode?.worldPosition!!
            val width = parentNode.renderable?.view?.width!!
            val height = parentNode.renderable?.view?.height!!
            position = Position(x = width * pos.x, y = height * pos.y, z = pos.z)
            parent = parentNode
        }

        // 점 노드의 worldPosition를 디바이스 화면에 포인트로 변환
        val screenPoint = binding.sceneView.cameraNode.worldToScreenPoint(
            pointNode?.worldPosition?.toVector3()
        )

        //변환된 포인트로, MainActivity에 있는 점을 이동시킨다.
        movingPoint
            .animate()
            .x(screenPoint.x)
            .y(screenPoint.y)
            .setDuration(0)
            .start()
    }

    //사각형의 꼭지점 스크린 포인트 가져오기
    private fun getScreenPoint(widthRatio: Float = 2.0f, heightRatio: Float = 2.0f): Vector3 {
        val vw = findViewById<View>(android.R.id.content)
        return Vector3(vw.width / widthRatio, vw.height / heightRatio, 0f)
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
                    if (kotlin.math.abs(event?.values!![1]) > kotlin.math.abs(event.values[0])) {
                        //Mainly portrait
                        if (event.values[1] > 1) {
                            //Portrait
                        } else if (event.values[1] < -1) { //Inverse portrait
                        }
                        //방위각을 각도로 변환하고,
                        //해당 각도에 x값을 넣는다.
                        nodeChangePortDegree = Math.toDegrees(azimuthX).toFloat()
                        isLandScape = false
                    } else {
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

        if(photoFrameNode != null)
            binding.sceneView.removeChild(photoFrameNode!!)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager?.unregisterListener(this)
    }

}