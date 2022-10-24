package com.sooyeol.sceneviewuses

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
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Pose
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Vector3
import com.sooyeol.sceneviewuses.databinding.ActivityMainBinding
import io.github.sceneview.math.Position
import io.github.sceneview.math.toFloat3
import io.github.sceneview.math.toVector3


class MainActivity : AppCompatActivity(), OnFrameListener, SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private var photoFrameNode: PhotoFrameNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //센서매니저를 초기화 해주고, 실기기 디바이스의 회전값을 얻기 위해
        //가속도계 센서 유형과 자기장 센서 유형을 가져온다.
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
        accelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
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
        binding.sceneView.apply {
            planeRenderer.isVisible = false
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
        if(photoFrameNode != null) {
            photoFrameNode?.apply {

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

                // ray에서 얼마나 떨어져 있는지 설정한다.
                worldPosition = Position(ray?.getPoint(1.2f)?.toFloat3()!!)
                parent = camera

                val mappedFloat = FloatArray(4)
                binding.sceneView.currentFrame?.frame?.transformCoordinates2d(
                    Coordinates2d.VIEW,
                    floatArrayOf(renderable?.view?.left!!.toFloat(), renderable?.view?.top!!.toFloat(), renderable?.view?.right!!.toFloat(), renderable?.view?.bottom!!.toFloat()),
                    Coordinates2d.VIEW,
                    mappedFloat
                )
                // TODO: 문서 찾아보는게 훨씬 나음 찾아볼것 
//                val test = binding.sceneView.cameraNode.worldToScreenPoint(mappedFloat.toFloat3().toVector3())
//                Log.d("플롯2", test.toString())


                //left //top //right //bottom 이 순으로 하면 됨

//                Log.d("플롯", mappedFloat.map {  it.toString() }.toString())
            }

        }
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