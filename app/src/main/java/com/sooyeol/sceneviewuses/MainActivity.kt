package com.sooyeol.sceneviewuses

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.drawToBitmap
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.ar.core.Config
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Vector3
import com.sooyeol.sceneviewuses.databinding.ActivityMainBinding
import com.sooyeol.sceneviewuses.nodes.PhotoFrameNode
import com.sooyeol.sceneviewuses.nodes.PointNode
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.scene.PlaneRenderer
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.math.toVector3
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.util.*


class MainActivity : AppCompatActivity(), OnFrameListener, SensorEventListener {

    private lateinit var binding: ActivityMainBinding

    private var photoFrameNode: PhotoFrameNode? = null

    //화면 표시된 ScreenPoint(노드)
    private var leftTopNode: PointNode? = null
    private var rightTopNode: PointNode? = null
    private var leftDownNode: PointNode? = null
    private var rightDownNode: PointNode? = null

    //화면 표시된 ScreenPoint(좌표)
    private var leftTopPoint: Point? = null
    private var rightTopPoint: Point? = null
    private var leftDownPoint: Point? = null
    private var rightDownPoint: Point? = null

    //오픈cv를 사용할 준비가 되어있는지 아닌지
    private var isOpenCvEnabled = false

    //현재 사진을 비트맵으로 변환하는 중인지 아닌지
    private var isFilming = false

    //디바이스 방향 이넘 클래스
    enum class OrientationType {
        PORTRAIT,
        INVERSE_PORTRAIT,
        LEFT_LANDSCAPE,
        RIGHT_LANDSCAPE
    }

    private var currentOrientationType: OrientationType = OrientationType.PORTRAIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkIsSupportedDeviceOrFinish(this)) {
            Toast.makeText(applicationContext, "Device not supported", Toast.LENGTH_LONG)
                .show()
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //센서매니저를 초기화 해주고, 실기기 디바이스의 회전값을 얻기 위해
        //가속도계 센서 유형과 자기장 센서 유형을 가져온다.
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
        accelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        binding.btn.setOnClickListener {
            takePhoto()
        }
    }

    //권한체크 설정 메서드
    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        val openGlVersionString =
            (Objects.requireNonNull(
                activity
                    .getSystemService(Context.ACTIVITY_SERVICE)
            ) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Toast.makeText(
                activity,
                "Sceneform requires OpenGL ES $MIN_OPENGL_VERSION or later",
                Toast.LENGTH_LONG
            )
                .show()
            activity.finish()
            return false
        }
        return true
    }

    //사진찍는 기능의 메서드이다.
    private fun takePhoto() {

        ///촬영중이면 리턴
        if (isFilming) return

        isFilming = true

        val view = binding.sceneView
        //사진 찍을때 선이 나타나는 것을 방지하기 위해
        //프레임 노드를 없애주고, 사진완료시 나타나게 한다.
        //포인트ui들도 숨겨준다.
        photoFrameNode?.isVisible = false
        binding.let {
            it.leftTopPointUi.visibility = View.GONE
            it.rightTopPointUi.visibility = View.GONE
            it.leftDownPointUi.visibility = View.GONE
            it.rightDownPointUi.visibility = View.GONE
        }

        //sceneView 자체 전체화면을 비트맵으로 만들어준다.
        val bitmap = Bitmap.createBitmap(
            view.width, view.height,
            Bitmap.Config.ARGB_8888
        )

        lifecycleScope.launch {
            delay(500)
            //PixelCopy를 통해 비트맵을 추출한다.
            val handlerThread = HandlerThread("PixelCopier")
            handlerThread.start()
            // Make the request to copy.
            PixelCopy.request(view, bitmap, { copyResult: Int ->
                if (copyResult == PixelCopy.SUCCESS) {
                    try {
                        //오픈시브이를 활용할 수 있는 상태가 되면,
                        //perspectiveImage메서드에 비트맵을 넘겨준다.
                        if (isOpenCvEnabled) {
                            perspectiveImage(bitmap)
                        }

                    } catch (e: Exception) {
                        Log.d("에러", e.toString())
                    }

                }
                handlerThread.quitSafely()
            }, Handler(handlerThread.looper))
        }
    }

    //이미지를 perspective 변환해준다.
    private fun perspectiveImage(bitmap: Bitmap) {

        //비트맵을 담을 Mat
        val bitmapMat = Mat()

        //파라미터로 넘겨받은 비트맵을 Mat으로 변환하여 bitmapMat에 넣어준다.
        Utils.bitmapToMat(bitmap, bitmapMat)

        //스크린상의 포인트를 담을 배열들이다.
        val leftTopPointArr = IntArray(2)
        val rightTopPointArr = IntArray(2)
        val leftDownPointArr = IntArray(2)
        val rightDownPointArr = IntArray(2)

        //각 노드들의 스크린상의 포인트를 가져와서 인트 배열에 넣어준다.
        binding.let {
            it.leftTopPointUi.getLocationOnScreen(leftTopPointArr)
            it.rightTopPointUi.getLocationOnScreen(rightTopPointArr)
            it.leftDownPointUi.getLocationOnScreen(leftDownPointArr)
            it.rightDownPointUi.getLocationOnScreen(rightDownPointArr)
        }

        //포인트 x, y 값(Point) 변수에 넣어주기 넣어주기
        leftTopPoint = Point(leftTopPointArr[0].toDouble(), leftTopPointArr[1].toDouble())
        rightTopPoint = Point(rightTopPointArr[0].toDouble(), rightTopPointArr[1].toDouble())
        leftDownPoint = Point(leftDownPointArr[0].toDouble(), leftDownPointArr[1].toDouble())
        rightDownPoint = Point(rightDownPointArr[0].toDouble(), rightDownPointArr[1].toDouble())

        //이미지 사이즈(가로, 세로)를 결정한다.
        val view: ArSceneView = binding.sceneView
        val dw: Double = view.width.toDouble()
        val dh: Double = view.height.toDouble()

        //입력될 4개의 좌표값 (순서를 지켜준다.)
        val srcQuad: MatOfPoint2f

        //출력될 4개의 좌표점 지정
        val dstQuad: MatOfPoint2f

        //비트맵 각도
        val bitmapDegree: Float

        //디바이스 방향에 따른 입력값 순서를 바꾸어 준다.
        when (currentOrientationType) {
            OrientationType.PORTRAIT -> {
                srcQuad = MatOfPoint2f(
                    rightDownPoint,
                    rightTopPoint,
                    leftTopPoint,
                    leftDownPoint
                )

                dstQuad = MatOfPoint2f(
                    Point(0.0, 0.0),
                    Point(0.0, dh),
                    Point(dw, dh),
                    Point(dw, 0.0)
                )

                bitmapDegree = 180f
            }
            OrientationType.RIGHT_LANDSCAPE -> {
                srcQuad = MatOfPoint2f(
                    leftDownPoint,
                    rightDownPoint,
                    rightTopPoint,
                    leftTopPoint
                )

                dstQuad = MatOfPoint2f(
                    Point(0.0, dh),
                    Point(dw, dh),
                    Point(dw, 0.0),
                    Point(0.0, 0.0)
                )

                bitmapDegree = 0f
            }
            OrientationType.LEFT_LANDSCAPE -> {

                srcQuad = MatOfPoint2f(
                    rightTopPoint,
                    leftTopPoint,
                    leftDownPoint,
                    rightDownPoint
                )

                dstQuad = MatOfPoint2f(
                    Point(dw, 0.0),
                    Point(0.0, 0.0),
                    Point(0.0, dh),
                    Point(dw, dh)
                )

                bitmapDegree = 0f
            }
            else -> {
                srcQuad = MatOfPoint2f(
                    leftTopPoint,
                    leftDownPoint,
                    rightDownPoint,
                    rightTopPoint
                )

                dstQuad = MatOfPoint2f(
                    Point(0.0, 0.0),
                    Point(0.0, dh),
                    Point(dw, dh),
                    Point(dw, 0.0)
                )

                bitmapDegree = 0f
            }
        }

        //원근변환계산
        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
        //변환한 결과값을 받을 Mat
        val newDst = Mat()
        //변환 계산 후 Mat에 넣어주기
        Imgproc.warpPerspective(
            bitmapMat,
            newDst,
            perspectiveTransform,
            org.opencv.core.Size(dw, dh)
        )
        //변환된 값이 담긴 newDst를 비트맵으로 변경하고, 사진도 회전해서 출력
        runOnUiThread {
            Glide.with(this)
                .load(newDst.toBitmap().rotate(bitmapDegree)) //180
                .into(binding.imageView)
            binding.imageView.visibility = View.VISIBLE
            //숨겨든 프레임 노드를 다시 보여주고
            photoFrameNode?.isVisible = true
            //없앤 포인트 ui들도 다시 보이게 한다.
            binding.let {
                it.leftTopPointUi.visibility = View.VISIBLE
                it.rightTopPointUi.visibility = View.VISIBLE
                it.leftDownPointUi.visibility = View.VISIBLE
                it.rightDownPointUi.visibility = View.VISIBLE
            }
            //촬영완료
            isFilming = false
        }
    }

    //비트맵 원하는 각도로 회전하는 메서드
    fun Bitmap.rotate(degrees: Float): Bitmap {
        Matrix().let {
            // set Degrees
            it.postRotate(degrees)
            return Bitmap.createBitmap(this, 0, 0, width, height, it, true)
        }
    }

    //Mat 비트맵으로 만들기
    fun Mat.toBitmap(code: Int? = null): Bitmap {
        if (code != null) {
            Imgproc.cvtColor(this, this, code)
        }
        return Bitmap.createBitmap(
            cols(), rows(), Bitmap.Config.ARGB_8888
        ).apply {
            Utils.matToBitmap(this@toBitmap, this)
        }
    }

    override fun onBackPressed() {
        binding.imageView.visibility = View.GONE
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
        photoFrameNode?.let {
            it.isSelected = false
            it.isEditable = false
            it.isSelectable = false
            it.isPositionEditable = false
            it.isRotationEditable = false
            it.isScaleEditable = false


        }

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
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
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
        Log.d("바닥", binding.sceneView.planeRenderer.toString())
        val camera = binding.sceneView.cameraNode
        //노드가 있다면
        photoFrameNode?.apply {
            val ray: Ray?
            // 디바이스 화면 기준으로 노드를 생성할 위치를 가져온다.
            val screenPoint = getScreenPoint()

            //화면을 마주보게 만들어주는 screenPointToRay를 사용하여, x y 값을 넣어준다.
            ray = camera.screenPointToRay(
                screenPoint.x, screenPoint.y
            )

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

            worldPosition = Position(ray?.getPoint(1.2f)?.toFloat3()!!)

            parent = camera

        }?.also {
            it.smooth(
                quaternion = it.quaternion,
                speed = 20.0f,
                position = it.position
            )
        }


        //점 노드 찍기

        //왼쪽 위 점
        setPointNode(
            pointNode = leftTopNode,
            pos = Position(x = -0.00076f, y = 0.00075f, z = 0.013f),
            movingPoint = binding.leftTopPointUi
        )

        //오른쪽 위 점
        setPointNode(
            pointNode = rightTopNode,
            pos = Position(x = 0.000724f, y = 0.00077f, z = 0.001f),
            movingPoint = binding.rightTopPointUi
        )

        //왼쪽 아래 점
        setPointNode(
            pointNode = leftDownNode,
            pos = Position(x = -0.00077f, y = -0.00071f, z = 0.009f),
            movingPoint = binding.leftDownPointUi
        )

        //오른쪽 아래 점
        setPointNode(
            pointNode = rightDownNode,
            pos = Position(x = 0.00072f, y = -0.00074f, z = -0.005f),
            movingPoint = binding.rightDownPointUi
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
                            currentOrientationType = OrientationType.PORTRAIT
                            //Portrait
                        } else if (event.values[1] < -1) { //Inverse portrait
                            currentOrientationType = OrientationType.INVERSE_PORTRAIT
                        }
                        //방위각을 각도로 변환하고,
                        //해당 각도에 x값을 넣는다.
                        nodeChangePortDegree = Math.toDegrees(azimuthX).toFloat()
                        isLandScape = false
                    } else {
                        //Mainly landscape
                        if (event.values[0] > 1) {
                            //Landscape - right side up
                            currentOrientationType = OrientationType.LEFT_LANDSCAPE
                        } else if (event.values[0] < -1) {
                            //Landscape - left side up
                            currentOrientationType = OrientationType.RIGHT_LANDSCAPE
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

        if (photoFrameNode != null)
            binding.sceneView.removeChild(photoFrameNode!!)

        //opnecv가 초기화가 됬는지 유무에 따라 isOpenCvEnabled값을 달리 저장한다.
        if (!OpenCVLoader.initDebug()) {
            Log.d(
                "OpenCV",
                "Internal OpenCV library not found. Using OpenCV Manager for initialization"
            )
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback)
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    isOpenCvEnabled = true
                }
                else -> {
                    super.onManagerConnected(status)
                    isOpenCvEnabled = false
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mSensorManager?.unregisterListener(this)
    }

    companion object {
        //오픈지엘 최소 버전
        private const val MIN_OPENGL_VERSION = 3.0
    }

}