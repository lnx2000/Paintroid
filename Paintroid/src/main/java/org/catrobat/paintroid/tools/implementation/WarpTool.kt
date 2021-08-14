package org.catrobat.paintroid.tools.implementation

import android.graphics.*
import org.catrobat.paintroid.command.CommandManager
import org.catrobat.paintroid.tools.ContextCallback
import org.catrobat.paintroid.tools.ToolPaint
import org.catrobat.paintroid.tools.ToolType
import org.catrobat.paintroid.tools.Workspace
import org.catrobat.paintroid.tools.options.ToolOptionsVisibilityController
import kotlin.math.pow

class WarpTool(
    override var contextCallback: ContextCallback,
    toolOptionsViewController: ToolOptionsVisibilityController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    commandManager: CommandManager,
    override var drawTime: Long
) : BaseTool(contextCallback, toolOptionsViewController, toolPaint, workspace, commandManager) {

    private lateinit var coordinates: List<Pair<Float, Float>>
    private var bitmap: Bitmap = workspace.bitmapOfCurrentLayer!!
    private var tempBitmap: Bitmap =
        Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    private var tempCanvas = Canvas(tempBitmap)
    private val paint: Paint = Paint()

    companion object {
        const val PADDING_TOP = 0
        const val PADDING_RIGHT = 0
        const val PADDING_BOTTOM = 0
        const val PADDING_LEFT = 0
        const val MESH_WIDTH = 30
        const val MESH_HEIGHT = 30
    }

    init {
        paint.color = Color.BLACK
        setup()
    }

    fun setup() {
        generateCoordinates()
    }

    private fun generateCoordinates() {
        coordinates = generateCoordinate(
            bitmap.width,
            bitmap.height
        )

    }

    private fun generateCoordinate(
        width: Int,
        height: Int
    ): List<Pair<Float, Float>> {

        val widthSlice = (width - (PADDING_LEFT + PADDING_RIGHT)) / (MESH_WIDTH)
        val heightSlice = (height - (PADDING_TOP + PADDING_BOTTOM)) / (MESH_HEIGHT)

        val coordinates = mutableListOf<Pair<Float, Float>>()

        for (y in 0..MESH_WIDTH) {
            for (x in 0..MESH_HEIGHT) {
                coordinates.add(
                    Pair(
                        (x * widthSlice + PADDING_LEFT).toFloat(),
                        (y * heightSlice + PADDING_TOP).toFloat()
                    )
                )
            }
        }

        return coordinates
    }

    override fun draw(canvas: Canvas) {

        tempCanvas.drawBitmapMesh(
            bitmap,
            MESH_WIDTH,
            MESH_HEIGHT,
            coordinates.flatMap { listOf(it.first, it.second) }.toFloatArray(),
            0,
            null,
            0,
            null
        )
        workspace.bitmapOfCurrentLayer = tempBitmap
    }


    override val toolType: ToolType
        get() = ToolType.WARP


    override fun handleDown(coordinate: PointF?): Boolean {
        return handleMotion(coordinate)

    }

    override fun handleMove(coordinate: PointF?): Boolean {
        return handleMotion(coordinate)
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        return handleMotion(coordinate)
    }

    private fun handleMotion(coordinate: PointF?): Boolean {
        val sorted = coordinates.sortedBy {
            (it.first - coordinate!!.x).pow(2) + (it.second - coordinate.y).pow(2)
        }
        val selectedIndex = coordinates.indexOf(sorted[0])
        coordinates =
            coordinates.mapIndexed { index, pair -> if (index == selectedIndex) (coordinate!!.x to coordinate.y) else pair }

        return true
    }
}