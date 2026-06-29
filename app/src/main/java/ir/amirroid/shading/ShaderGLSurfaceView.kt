package ir.amirroid.shading

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

sealed class ShaderCompileResult {
    object Success : ShaderCompileResult()
    data class Error(val message: String) : ShaderCompileResult()
}

class ShaderGLSurfaceView(context: Context, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs) {

    val renderer = ShaderRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun updateShader(fragmentSource: String, onResult: (ShaderCompileResult) -> Unit) {
        queueEvent {
            val result = renderer.compileFragmentShader(fragmentSource)
            post { onResult(result) }
        }
    }
}

class ShaderRenderer : GLSurfaceView.Renderer {

    private val vertexShaderSource = """
        attribute vec2 aPosition;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """.trimIndent()

    private var program = 0
    private var positionHandle = 0
    private var timeHandle = 0
    private var resolutionHandle = 0

    private val dynamicUniforms = mutableListOf<Pair<String, Int>>()

    val uniformValues = ConcurrentHashMap<String, Float>()
    @Volatile var discoveredUniforms: List<String> = emptyList()

    private var width = 0
    private var height = 0
    private val startTime = System.nanoTime()
    private var lastShaderError: String? = null

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        compileFragmentShader(DEFAULT_FRAGMENT_SHADER)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return

        GLES20.glUseProgram(program)
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        val elapsed = (System.nanoTime() - startTime) / 1_000_000_000f
        GLES20.glUniform1f(timeHandle, elapsed)
        GLES20.glUniform2f(resolutionHandle, width.toFloat(), height.toFloat())

        for ((name, handle) in dynamicUniforms) {
            GLES20.glUniform1f(handle, uniformValues[name] ?: 0f)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    fun compileFragmentShader(fragmentSource: String): ShaderCompileResult {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        if (vertex == 0) return ShaderCompileResult.Error(lastShaderError ?: "Vertex shader error")

        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragment == 0) {
            GLES20.glDeleteShader(vertex)
            return ShaderCompileResult.Error(lastShaderError ?: "Fragment shader error")
        }

        val newProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(newProgram, vertex)
        GLES20.glAttachShader(newProgram, fragment)
        GLES20.glLinkProgram(newProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(newProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)

        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(newProgram)
            GLES20.glDeleteProgram(newProgram)
            return ShaderCompileResult.Error(log ?: "Link error")
        }

        if (program != 0) GLES20.glDeleteProgram(program)
        program = newProgram
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")
        resolutionHandle = GLES20.glGetUniformLocation(program, "uResolution")

        dynamicUniforms.clear()
        val names = extractUniforms(fragmentSource)
        for (name in names) {
            val handle = GLES20.glGetUniformLocation(program, name)
            dynamicUniforms.add(name to handle)
            uniformValues.putIfAbsent(name, 0f)
        }
        uniformValues.keys.retainAll(names.toSet())
        discoveredUniforms = names

        return ShaderCompileResult.Success
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            lastShaderError = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private val BUILTIN_UNIFORMS = setOf("uTime", "uResolution")
        private val UNIFORM_REGEX = Regex("""uniform\s+float\s+(\w+)\s*;""")

        fun extractUniforms(source: String): List<String> =
            UNIFORM_REGEX.findAll(source)
                .map { it.groupValues[1] }
                .filter { it !in BUILTIN_UNIFORMS }
                .distinct()
                .toList()

        val DEFAULT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform float uTime;
            uniform vec2 uResolution;
            uniform float uParam0;
            uniform float uParam1;

            void main() {
                vec2 uv = gl_FragCoord.xy / uResolution.xy;
                vec3 color = 0.5 + 0.5 * cos(uTime + uParam0 * 6.0 + uv.xyx * 6.2831 + vec3(0.0, 2.0, 4.0));
                gl_FragColor = vec4(color, 1.0);
            }
        """.trimIndent()
    }
}