package com.sooyeol.sceneviewuses

import android.R
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.ar.core.Config
import com.google.ar.sceneform.CameraNode
import com.google.ar.sceneform.math.Vector3
import com.sooyeol.sceneviewuses.databinding.ActivityMainBinding
import com.sooyeol.sceneviewuses.nodes.PhotoFrameNode
import com.sooyeol.sceneviewuses.nodes.PointNode
import dev.romainguy.kotlin.math.*
import dev.romainguy.kotlin.math.RotationsOrder
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.position
import io.github.sceneview.math.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.util.*


class MainActivity : AppCompatActivity(), OnFrameListener {

    private lateinit var binding: ActivityMainBinding

    //액자 노드
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

    //이미지를 perspective 변환해준다.
    private fun perspectiveImage(bitmap: Bitmap) {

        //비트맵을 담을 Mat
        val bitmapMat = Mat()

        //파라미터로 넘겨받은 비트맵을 Mat으로 변환하여 bitmapMat에 넣어준다.
        Utils.bitmapToMat(bitmap, bitmapMat)

        //포인트 x, y 값(Point) 변수에 넣어주기 넣어주기
        leftTopPoint =
            Point(binding.leftTopPointUi.x.toDouble(), binding.leftTopPointUi.y.toDouble())
        rightTopPoint =
            Point(binding.rightTopPointUi.x.toDouble(), binding.rightTopPointUi.y.toDouble())
        leftDownPoint =
            Point(binding.leftDownPointUi.x.toDouble(), binding.leftDownPointUi.y.toDouble())
        rightDownPoint =
            Point(binding.rightDownPointUi.x.toDouble(), binding.rightDownPointUi.y.toDouble())

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
            size = android.util.Size(binding.sceneView.width, binding.sceneView.height)
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

        //이렇게 설정해두면, onResume상태에서도 노드가 중복되어 그려지지 않는다.
        binding.sceneView.arSessionConfig?.apply {
            cloudAnchorMode = Config.CloudAnchorMode.ENABLED
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }
    }

    override fun onFrame() {

        //하나의 플레인을 완전히 추적했는지
        val isTrackingPlane = binding.sceneView.arSession?.isTrackingPlane

        if (isTrackingPlane == true) {
            //노드가 있다면
            photoFrameNode?.apply {

                val center = screenCenter()

                val hitTest = binding.sceneView.currentFrame?.frame?.hitTest(
                    center.x, center.y
                )
                val hasTestIterator = hitTest?.iterator()

                if (hasTestIterator?.hasNext() == true) {
                    val hitResult = hasTestIterator.next()
                    position = hitResult.hitPose.position
                    val cameraNode = binding.sceneView.cameraNode
                    //RotationOrder = > 오일러각도 축의 우선순위를 둔다. Y를 우선순위
                    val cameraAngle = eulerAngles(cameraNode.quaternion, RotationsOrder.YXZ)
                    rotation = Rotation(
                        x = -90f,
                        y = cameraAngle.z + cameraAngle.y,
                        z = 0f
                    )

                    //카메라와 노드의 거리 넘겨주기
//                    getCameraFov(hitResult.distance)
                    getCameraFov(cameraNode, hitResult.distance)
                }
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

    override fun onResume() {
        super.onResume()

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

    private fun screenCenter(): Vector3 {
        val vw = findViewById<View>(R.id.content)
        return Vector3(vw.width / 2f, vw.height / 2f, 0f)
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

    companion object {
        //오픈지엘 최소 버전
        private const val MIN_OPENGL_VERSION = 3.0
    }

    private fun getCameraFov(cameraNode: CameraNode, distance: Float) {
        val camera = cameraNode.camera

        val focalLength = camera?.focalLength
        val w = binding.sceneView.width
        val h = binding.sceneView.height

        val fovW = Math.toDegrees(2 * Math.atan(w / (focalLength?.times(2.0)!!))) * distance
        val fovH = Math.toDegrees(2 * Math.atan(h / (focalLength * 2.0))) * distance

        val scale = ((w / fovW + h / fovH) / 2).toFloat()

        photoFrameNode?.scale = Scale(scale)

        Log.d("길이", "가로 : $fovW")
        Log.d("길이", "세로 : $fovH")
    }
}