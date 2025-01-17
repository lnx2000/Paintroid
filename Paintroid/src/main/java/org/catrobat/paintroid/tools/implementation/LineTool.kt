/*
 * Paintroid: An image manipulation application for Android.
 * Copyright (C) 2010-2021 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.paintroid.tools.implementation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.VisibleForTesting
import org.catrobat.paintroid.command.CommandManager
import org.catrobat.paintroid.command.serialization.SerializablePath
import org.catrobat.paintroid.tools.ContextCallback
import org.catrobat.paintroid.tools.ToolPaint
import org.catrobat.paintroid.tools.ToolType
import org.catrobat.paintroid.tools.Workspace
import org.catrobat.paintroid.tools.common.CommonBrushChangedListener
import org.catrobat.paintroid.tools.common.CommonBrushPreviewListener
import org.catrobat.paintroid.tools.common.LINE_THRESHOLD
import org.catrobat.paintroid.tools.options.BrushToolOptionsView
import org.catrobat.paintroid.tools.options.ToolOptionsVisibilityController
import kotlin.math.abs

class LineTool(
    private val brushToolOptionsView: BrushToolOptionsView,
    contextCallback: ContextCallback,
    toolOptionsViewController: ToolOptionsVisibilityController,
    toolPaint: ToolPaint,
    workspace: Workspace,
    commandManager: CommandManager,
    override var drawTime: Long
) : BaseToolWithShape(
    contextCallback,
    toolOptionsViewController,
    toolPaint,
    workspace,
    commandManager
) {
    @VisibleForTesting
    var lineFinalized: Boolean = false

    @VisibleForTesting
    var endpointSet: Boolean = false

    @VisibleForTesting
    var startpointSet: Boolean = false

    @VisibleForTesting
    var initialEventCoordinate: PointF? = null

    @VisibleForTesting
    var startPointToDraw: PointF? = null

    @VisibleForTesting
    var endPointToDraw: PointF? = null

    @VisibleForTesting
    var currentCoordinate: PointF? = null

    override var toolType: ToolType = ToolType.LINE

    var pointToDelete: PointF? = null

    var toolSwitched: Boolean = false

    var lastSetStrokeWidth: Int = 0

    init {
        brushToolOptionsView.setBrushChangedListener(CommonBrushChangedListener(this))
        brushToolOptionsView.setBrushPreviewListener(
            CommonBrushPreviewListener(
                toolPaint,
                toolType
            )
        )
        brushToolOptionsView.setCurrentPaint(toolPaint.paint)
    }

    override fun draw(canvas: Canvas) {
        initialEventCoordinate?.let { initialCoordinate ->
            currentCoordinate?.let { currentCoordinate ->
                canvas.run {
                    save()
                    clipRect(0, 0, workspace.width, workspace.height)
                    drawLine(
                        initialCoordinate.x,
                        initialCoordinate.y, currentCoordinate.x,
                        currentCoordinate.y, toolPaint.previewPaint
                    )
                    restore()
                }
            }
        }
    }

    override fun drawShape(canvas: Canvas) {
        // This should never be invoked
    }

    override fun onClickOnButton() {
        if (startpointSet && endpointSet) {
            if (toolSwitched) {
                val startX = startPointToDraw?.x
                val startY = startPointToDraw?.y
                val endX = endPointToDraw?.x
                val endY = endPointToDraw?.y
                val finalPath = SerializablePath().apply {
                    if (startX != null && startY != null && endX != null && endY != null) {
                        moveTo(startX, startY)
                        lineTo(endX, endY)
                    }
                }
                lineFinalized = true
                toolSwitched = false
                val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)
                commandManager.addCommand(command)
            }
            lineFinalized = true
            resetInternalState()
        } else if (startpointSet && !endpointSet) {
            if (commandManager.isUndoAvailable) {
                commandManager.undo()
            }
            lineFinalized = true
            resetInternalState()
        } else {
            resetInternalState()
        }
    }

    override fun handleDown(coordinate: PointF?): Boolean {
        coordinate ?: return false
        initialEventCoordinate = PointF(coordinate.x, coordinate.y)
        previousEventCoordinate = PointF(coordinate.x, coordinate.y)
        return true
    }

    override fun handleMove(coordinate: PointF?): Boolean {
        coordinate ?: return false
        currentCoordinate = PointF(coordinate.x, coordinate.y)
        return true
    }

    fun handleStartPoint(xDistance: Float, yDistance: Float): Boolean {
        startPointToDraw = previousEventCoordinate
        startPointToDraw?.x = xDistance.let { startPointToDraw?.x?.minus(it) }
        startPointToDraw?.y = yDistance.let { startPointToDraw?.y?.minus(it) }

        if (startPointToDraw?.let { workspace.contains(it) } == true) {
            startpointSet = true
            resetInternalState()
            startPointToDraw?.let {
                return addPointCommand(it)
            }
        } else {
            lineFinalized = true
            resetInternalState()
        }
        return true
    }

    fun handleEndPoint(xDistance: Float, yDistance: Float): Boolean {
        endPointToDraw = previousEventCoordinate
        endPointToDraw?.x = xDistance.let { endPointToDraw?.x?.minus(it) }
        endPointToDraw?.y = yDistance.let { endPointToDraw?.y?.minus(it) }
        endpointSet = true
        val startX = startPointToDraw?.x
        val startY = startPointToDraw?.y
        val endX = endPointToDraw?.x
        val endY = endPointToDraw?.y
        if (commandManager.isUndoAvailable) {
            commandManager.undo()
        }
        val finalPath = SerializablePath().apply {
            if (startX != null && startY != null && endX != null && endY != null) {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
        }
        val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)
        commandManager.addCommand(command)
        resetInternalState()
        return true
    }

    fun handleNormalLine(coordinate: PointF): Boolean {
        val bounds = RectF()
        if (startpointSet) {
            resetInternalState()
            return true
        }
        val finalPath = SerializablePath().apply {
            moveTo(
                initialEventCoordinate?.x ?: return false,
                initialEventCoordinate?.y ?: return false
            )
            lineTo(coordinate.x, coordinate.y)
            computeBounds(bounds, true)
        }
        bounds.inset(-toolPaint.strokeWidth, -toolPaint.strokeWidth)

        if (workspace.intersectsWith(bounds)) {
            val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)
            commandManager.addCommand(command)
        }
        resetInternalState()
        return true
    }

    override fun handleUp(coordinate: PointF?): Boolean {
        if (initialEventCoordinate == null || previousEventCoordinate == null || coordinate == null) {
            return false
        }
        val xDistance = initialEventCoordinate?.x?.minus(coordinate.x)
        val yDistance = initialEventCoordinate?.y?.minus(coordinate.y)
        if (xDistance != null && yDistance != null) {
            if (abs(xDistance) > LINE_THRESHOLD || abs(yDistance) > LINE_THRESHOLD) {
                return handleNormalLine(coordinate)
            } else if (!startpointSet) {
                return handleStartPoint(xDistance, yDistance)
            } else {
                return handleEndPoint(xDistance, yDistance)
            }
        }
        return true
    }

    override fun resetInternalState() {
        initialEventCoordinate = null
        currentCoordinate = null
        if (lineFinalized) {
            startPointToDraw = null
            endPointToDraw = null
            startpointSet = false
            endpointSet = false
            lineFinalized = false
        }
    }

    override fun changePaintColor(color: Int) {
        super.changePaintColor(color)
        if (startpointSet && endpointSet) {
            val startX = startPointToDraw?.x
            val startY = startPointToDraw?.y
            val endX = endPointToDraw?.x
            val endY = endPointToDraw?.y
            if (commandManager.isUndoAvailable) {
                commandManager.undo()
                val finalPath = SerializablePath().apply {
                    if (startX != null && startY != null && endX != null && endY != null) {
                        moveTo(startX, startY)
                        lineTo(endX, endY)
                    }
                }
                val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)
                commandManager.addCommand(command)
            }
        } else if (startpointSet && !endpointSet && !lineFinalized) {
            if (commandManager.isUndoAvailable) {
                commandManager.undo()
                startPointToDraw?.let { addPointCommand(it) }
            }
        }
        brushToolOptionsView.invalidate()
    }

    override fun changePaintStrokeWidth(strokeWidth: Int) {
        super.changePaintStrokeWidth(strokeWidth)
        val noNewLine = lastSetStrokeWidth == strokeWidth
        if (startpointSet && endpointSet && !noNewLine) {
            val startX = startPointToDraw?.x
            val startY = startPointToDraw?.y
            val endX = endPointToDraw?.x
            val endY = endPointToDraw?.y
            if (commandManager.isUndoAvailable) {
                commandManager.undo()
                val finalPath = SerializablePath().apply {
                    if (startX != null && startY != null && endX != null && endY != null) {
                        moveTo(startX, startY)
                        lineTo(endX, endY)
                    }
                }
                val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)
                commandManager.addCommand(command)
            }
        } else if (startpointSet && !endpointSet && !lineFinalized && !noNewLine) {
            if (commandManager.isUndoAvailable) {
                commandManager.undo()
                startPointToDraw?.let { addPointCommand(it) }
            }
        }
        lastSetStrokeWidth = strokeWidth
        brushToolOptionsView.invalidate()
    }

    override fun changePaintStrokeCap(cap: Paint.Cap) {
        super.changePaintStrokeCap(cap)
        if (startpointSet && endpointSet) {
            val startX = startPointToDraw?.x
            val startY = startPointToDraw?.y
            val endX = endPointToDraw?.x
            val endY = endPointToDraw?.y
            if (commandManager.isUndoAvailable) {
                commandManager.undo()
                val finalPath = SerializablePath().apply {
                    if (startX != null && startY != null && endX != null && endY != null) {
                        moveTo(startX, startY)
                        lineTo(endX, endY)
                    }
                }
                val command = commandFactory.createPathCommand(toolPaint.paint, finalPath)
                commandManager.addCommand(command)
            }
        } else if (startpointSet && !endpointSet && !lineFinalized) {
            if (commandManager.isUndoAvailable) {
                commandManager.undo()
                startPointToDraw?.let { addPointCommand(it) }
            }
        }
        brushToolOptionsView.invalidate()
    }

    private fun addPointCommand(coordinate: PointF): Boolean {
        val command = commandFactory.createPointCommand(this.drawPaint, coordinate)
        commandManager.addCommand(command)
        return true
    }
}
