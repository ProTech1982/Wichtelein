package app.wichtelein.ar

import android.app.Activity
import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import app.wichtelein.anim.WichtelProjection
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.sqrt

/** Was die AR-Sitzung in einem Bild weiß (vom GL-Thread für die Oberfläche kopiert). */
class ArSnapshot(
    /** Projektion × Ansicht (spaltenweise, 4 × 4) */
    val viewProjection: FloatArray,
    /** Fußpunkt des Wichtels im Raum (Anker) oder null */
    val anchor: FloatArray?,
    val right: FloatArray,
    val toward: FloatArray,
    val up: FloatArray,
    val viewW: Int,
    val viewH: Int,
    val tracking: Boolean,
    val planeCount: Int,
    /** Wie oft der Nutzer einen neuen Platz gewählt hat (zum Neustart der Aufführung) */
    val placement: Int,
)

/**
 * Bildet die Aufführung (x, y relativ) auf den Boden im Raum ab: der Anker ist der Startpunkt, x geht nach rechts,
 * y nach vorn (zur Kamera hin). [metersPerUnit] sagt, wie viele Meter eine Einheit der Aufführung sind.
 */
class ArProjector(
    private val s: ArSnapshot,
    private val originX: Float,
    private val originY: Float,
    private val metersPerUnit: Float,
    private val elfMeters: Float,
) : WichtelProjection {
    private fun world(x: Float, y: Float): FloatArray? {
        val a = s.anchor ?: return null
        val dx = (x - originX) * metersPerUnit
        val dz = (y - originY) * metersPerUnit
        return floatArrayOf(
            a[0] + s.right[0] * dx + s.toward[0] * dz,
            a[1] + s.right[1] * dx + s.toward[1] * dz,
            a[2] + s.right[2] * dx + s.toward[2] * dz,
        )
    }

    private fun project(p: FloatArray): Offset? {
        val m = s.viewProjection
        val cw = m[3] * p[0] + m[7] * p[1] + m[11] * p[2] + m[15]
        if (cw <= 0.01f) return null
        val cx = m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12]
        val cy = m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13]
        return Offset((cx / cw * 0.5f + 0.5f) * s.viewW, (1f - (cy / cw * 0.5f + 0.5f)) * s.viewH)
    }

    override fun position(x: Float, y: Float): Offset? = world(x, y)?.let { project(it) }

    override fun elfHeight(x: Float, y: Float): Float {
        val w = world(x, y) ?: return 0f
        val top = floatArrayOf(w[0] + s.up[0] * elfMeters, w[1] + s.up[1] * elfMeters, w[2] + s.up[2] * elfMeters)
        val a = project(w) ?: return 0f
        val b = project(top) ?: return 0f
        return abs(a.y - b.y)
    }
}

/**
 * Zeichnet das Kamerabild der AR-Sitzung (OpenGL) und liefert pro Bild die Lage von Boden, Anker und Kamera.
 * Der Wichtel selbst wird von der Oberfläche (Compose) darübergezeichnet.
 */
class ArRenderer(
    private val activity: Activity,
    private val onSnapshot: (ArSnapshot) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile var session: Session? = null

    private val taps = ConcurrentLinkedQueue<FloatArray>()
    @Volatile private var clearRequested = false
    private var anchor: Anchor? = null
    private var placement = 0
    private val basisRight = floatArrayOf(1f, 0f, 0f)
    private val basisToward = floatArrayOf(0f, 0f, 1f)
    private val basisUp = floatArrayOf(0f, 1f, 0f)

    private var texId = 0
    private var program = 0
    private var posLoc = 0
    private var texLoc = 0
    private var viewW = 1
    private var viewH = 1
    private var viewportChanged = true
    private val quad = floatArrayOf(-1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f)
    private val texOut = FloatArray(8)
    private val quadBuf: FloatBuffer = direct(quad)
    private val texBuf: FloatBuffer = direct(FloatArray(8))
    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val vp = FloatArray(16)

    private fun direct(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

    fun tap(x: Float, y: Float) {
        taps.add(floatArrayOf(x, y))
    }

    fun clearAnchor() {
        clearRequested = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        posLoc = GLES20.glGetAttribLocation(program, "a_Position")
        texLoc = GLES20.glGetAttribLocation(program, "a_TexCoord")
        viewportChanged = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width.coerceAtLeast(1)
        viewH = height.coerceAtLeast(1)
        viewportChanged = true
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        try {
            s.setCameraTextureName(texId)
            if (viewportChanged) {
                @Suppress("DEPRECATION")
                val rotation = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
                s.setDisplayGeometry(rotation, viewW, viewH)
                viewportChanged = false
            }
            val frame = s.update()
            if (frame.hasDisplayGeometryChanged()) {
                frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, texOut)
                texBuf.position(0)
                texBuf.put(texOut)
                texBuf.position(0)
            }
            drawBackground()

            val camera = frame.camera
            val tracking = camera.trackingState == TrackingState.TRACKING
            if (clearRequested) {
                clearRequested = false
                anchor?.detach()
                anchor = null
            }
            while (true) {
                val t = taps.poll() ?: break
                if (!tracking) continue
                for (hit in frame.hitTest(t[0], t[1])) {
                    val tr = hit.trackable
                    if (tr is Plane && tr.isPoseInPolygon(hit.hitPose) && tr.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                        anchor?.detach()
                        anchor = hit.createAnchor()
                        placement++
                        fixBasis(camera.pose.getTransformedAxis(2, -1f), anchor!!.pose.getTransformedAxis(1, 1f))
                        break
                    }
                }
            }
            camera.getViewMatrix(view, 0)
            camera.getProjectionMatrix(proj, 0, 0.05f, 60f)
            Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
            val a = anchor?.takeIf { it.trackingState == TrackingState.TRACKING }
            val pos = a?.pose?.translation
            val planes = s.getAllTrackables(Plane::class.java).count { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            onSnapshot(
                ArSnapshot(vp.copyOf(), pos?.copyOf(), basisRight.copyOf(), basisToward.copyOf(), basisUp.copyOf(), viewW, viewH, tracking, planes, placement),
            )
        } catch (_: CameraNotAvailableException) {
        } catch (_: Exception) {
        }
    }

    /** Legt beim Platzieren die Achsen fest: nach vorn = zur Kamera, nach rechts = Bildschirm-rechts auf dem Boden. */
    private fun fixBasis(forward: FloatArray, up: FloatArray) {
        val upN = normalize(up)
        val d = forward[0] * upN[0] + forward[1] * upN[1] + forward[2] * upN[2]
        val f = normalize(floatArrayOf(forward[0] - upN[0] * d, forward[1] - upN[1] * d, forward[2] - upN[2] * d))
        val r = normalize(
            floatArrayOf(
                f[1] * upN[2] - f[2] * upN[1],
                f[2] * upN[0] - f[0] * upN[2],
                f[0] * upN[1] - f[1] * upN[0],
            ),
        )
        for (i in 0..2) {
            basisUp[i] = upN[i]
            basisRight[i] = r[i]
            basisToward[i] = -f[i]
        }
    }

    private fun normalize(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return if (l < 1e-6f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    private fun drawBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        quadBuf.position(0)
        texBuf.position(0)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, quadBuf)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texBuf)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        return sh
    }

    companion object {
        private const val VERTEX = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """
        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """
    }
}
