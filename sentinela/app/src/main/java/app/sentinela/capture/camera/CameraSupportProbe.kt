package app.sentinela.capture.camera

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import app.sentinela.core.model.CaptureSupport

/**
 * Consulta de capacidade da câmera. A câmera concorrente (frontal + traseira
 * ao mesmo tempo) só existe quando o aparelho anuncia
 * `FEATURE_CAMERA_CONCURRENT`; não há como "forçar" — só detectar e habilitar.
 */
object CameraSupportProbe {

    fun probe(context: Context): CaptureSupport {
        val pm = context.packageManager
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        var rear = false
        var front = false
        runCatching {
            manager?.cameraIdList?.forEach { id ->
                val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                when (facing) {
                    CameraCharacteristics.LENS_FACING_BACK -> rear = true
                    CameraCharacteristics.LENS_FACING_FRONT -> front = true
                }
            }
        }
        if (!rear && !front) {
            rear = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        }
        val concurrent = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)
        return CaptureSupport(
            hasRearCamera = rear,
            hasFrontCamera = front,
            concurrentCameras = concurrent,
            canCaptureScreen = true,
        )
    }
}
