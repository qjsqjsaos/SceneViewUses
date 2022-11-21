package com.sooyeol.sceneviewuses

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.opengl.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide

import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.math.Vector3
import com.sooyeol.sceneviewuses.databinding.ActivityMainBinding
import com.sooyeol.sceneviewuses.nodes.PhotoFrameNode
import com.sooyeol.sceneviewuses.nodes.PointNode
import dev.romainguy.kotlin.math.*
import io.github.sceneview.ar.ArSceneView
import io.github.sceneview.ar.arcore.*
import io.github.sceneview.math.*
import kotlinx.coroutines.launch
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
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

    //수평과 수직의 감지 구분
//    Config.PlaneFindingMode.HORIZONTAL
    private var planeMode = Config.PlaneFindingMode.VERTICAL

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

        binding.transform.setOnClickListener {
            isVertical = !isVertical

            if (isVertical) {
                binding.sceneView.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            } else {
                binding.sceneView.planeFindingMode = Config.PlaneFindingMode.VERTICAL
            }
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
        val srcQuad: MatOfPoint2f = MatOfPoint2f(
            rightDownPoint,
            rightTopPoint,
            leftTopPoint,
            leftDownPoint
        )

        //출력될 4개의 좌표점 지정
        val dstQuad: MatOfPoint2f = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(0.0, dh),
            Point(dw, dh),
            Point(dw, 0.0)
        )

        //비트맵 각도
        val bitmapDegree: Float = 180f

        //원근변환계산
        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
        //변환한 결과값을 받을 Mat
        val newDst = Mat()
        //변환 계산 후 Mat에 넣어주기
        Imgproc.warpPerspective(
            bitmapMat,
            newDst,
            perspectiveTransform,
            Size(dw, dh)
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
        android.graphics.Matrix().let {
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
            size = android.util.Size(binding.sceneView.width / 2, binding.sceneView.height / 2)
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
            planeRenderer.isVisible = true
            planeRenderer.isShadowReceiver = true
            planeRenderer.isEnabled = true
            focusMode = Config.FocusMode.AUTO
            planeFindingMode = planeMode
            addChild(photoFrameNode!!)
        }

        //이렇게 설정해두면, onResume상태에서도 노드가 중복되어 그려지지 않는다.
        binding.sceneView.arSessionConfig?.apply {
            cloudAnchorMode = Config.CloudAnchorMode.ENABLED
            planeFindingMode = planeMode
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }
    }

    private fun getScreenPoint(widthRatio: Float = 2.0f, heightRatio: Float = 2.0f): Vector3 {
        val vw = findViewById<View>(android.R.id.content)
        return Vector3(vw.width / widthRatio, vw.height / heightRatio, 0f)
    }

    private var isVertical = true

    override fun onFrame() {

        //하나의 플레인을 완전히 추적했는지
        val center = getScreenPoint()

        val frame = binding.sceneView.currentFrame?.frame

        val hitTest = frame?.hitTest(
            center.x, center.y
        )
        val hasTestIterator = hitTest?.iterator()

        //노드가 있다면
        photoFrameNode?.apply {

            val cameraNode = binding.sceneView.cameraNode
            //플레인 추출하기
            val planeObj = frame?.getUpdatedTrackables(Plane::class.java)?.iterator()

            while (planeObj?.hasNext() == true) {
                val plane = planeObj.next() as Plane

                if (plane.trackingState == TrackingState.TRACKING) {

                    if (isVertical) {
//                            //플레인이 벽에 생길경우

//                            //좌표는 노드가 아닌 디바이스의 기준이다.
//
//                            val hitResult3 = hasTestIterator.iterator()
//                            while (hitResult3.hasNext()) {
//                                val hitResult = hitResult3.next()
//
//                                val hitResultRota =
//                                    hitResult.hitPose.rotation.toQuaternion(rotationOrder)
//                                        .toEulerAngles() //ZYX
//                                rotation = Rotation(
//                                    x = 0f,
//                                    y = if (hitResultRota.x < 0) hitResultRota.y + 180f else hitResultRota.y,
//                                    z = if (hitResultRota.x < 0) cameraAngle.z + 180f else cameraAngle.z,
//                                )
//                            }
                        if (hasTestIterator?.hasNext() == true) {

                            val hitResult = hasTestIterator.next()

                            val hitPose = hitResult.hitPose


//                                val planePose = plane.subsumedBy?.


//                                hitPose.rotateVector(floatArray)
//


//                                position = hitPose.position

//                                quaternion = com.google.ar.sceneform.math.Quaternion.multiply(
//                                    Quaternion(
//                                        x = 90f,
//                                        y = planePose.quaternion.y,
////                                        z = planePose.quaternion.z,
////                                        w = planePose.quaternion.w
//                                    ).toOldQuaternion(),
//                                    Quaternion(
//                                        x = cameraNode.quaternion.x,
//                                        y = cameraNode.quaternion.y,
//                                        z = cameraNode.quaternion.z,
////                                        w = cameraNode.quaternion.w
//                                    ).toEulerAngles().toQuaternion(RotationsOrder.XZY).toOldQuaternion()
//                                ).toNewQuaternion()
                            //XYZ XZY YXZ YZX ZXY ZYX
                            val rotationOrder = RotationsOrder.ZXY
//
                            //XYZ XZY YXZ YZX ZXY ZYX  ***YXZ y = cameraAngle.y , z = cameraAngle.z
                            // TODO: 회전값만 해결하면 끝
                            val cameraAngle = eulerAngles(
                                cameraNode.quaternion,
                                rotationOrder
                            )
                            lookTowards(
                                lookDirection = hitPose.yDirection
                            )
//                            lookTowards(
//                                lookDirection = plane.subsumedBy?.centerPose?.yDirection ?: plane.centerPose.yDirection
//                            )

                            Log.d("와이", plane.centerPose.yDirection.toString())


//                            val q1 = quaternion.toOldQuaternion()
//                            val q2 = com.google.ar.sceneform.math.Quaternion.axisAngle(
//                                Vector3(
//                                    0f,
//                                    0f,
//                                    1f
//                                ), cameraAngle.z
//                            )
//
//                            Log.d("제트", cameraAngle.z.toString())
//
//
//                            quaternion = com.google.ar.sceneform.math.Quaternion.multiply(q1, q2)
//                                .toNewQuaternion()


//                                lookAt(plane.c, upDirection = Direction(y = 1.0f))

                        }
                    } else {
                        //플레인이 수평면에 생길경우
                        //RotationOrder = > 오일러각도 축의 우선순위를 둔다. Y를 우선순위
                        val cameraAngle = eulerAngles(cameraNode.quaternion, RotationsOrder.YXZ)
                        //좌표는 노드가 아닌 디바이스의 기준이다.
                        rotation = Rotation(
                            x = -90f,
                            y = cameraAngle.z + cameraAngle.y,
                            z = 0f
                        )
                    }

                    val screenPoint = binding.sceneView
                    val ray = cameraNode.screenPointToRay(
                        (screenPoint.width / 2).toFloat(),
                        (screenPoint.height / 2).toFloat()
                    )
                    position = ray?.getPoint(1f)?.toFloat3()!!
                }
            }

            //점 노드 찍기

            lifecycleScope.launch {
                launch {
                    //왼쪽 위 점
                    setPointNode(
                        pointNode = leftTopNode,
                        pos = Position(x = -0.00076f, y = 0.00076f, z = 0.005f),
                        movingPoint = binding.leftTopPointUi
                    )
                }
                launch {
                    //오른쪽 위 점
                    setPointNode(
                        pointNode = rightTopNode,
                        pos = Position(x = 0.00073f, y = 0.00076f, z = 0.005f),
                        movingPoint = binding.rightTopPointUi
                    )
                }
                launch {
                    //왼쪽 아래 점
                    setPointNode(
                        pointNode = leftDownNode,
                        pos = Position(x = -0.00076f, y = -0.00074f, z = 0.005f),
                        movingPoint = binding.leftDownPointUi
                    )
                }
                launch {
                    //오른쪽 아래 점
                    setPointNode(
                        pointNode = rightDownNode,
                        pos = Position(x = 0.00073f, y = -0.00074f, z = 0.005f),
                        movingPoint = binding.rightDownPointUi
                    )
                }
            }

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
}