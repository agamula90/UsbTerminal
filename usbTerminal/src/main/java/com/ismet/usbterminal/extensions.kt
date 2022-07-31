package com.ismet.usbterminal

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.PointF
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ismet.usbterminal.data.Command
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.LayoutDialogMeasureBinding
import com.ismet.usbterminalnew.databinding.LayoutDialogOnOffBinding
import org.achartengine.model.XYSeries
import java.util.regex.Pattern

fun XYSeries.set(points: List<PointF>) {
    val pointsToAdd = points.toMutableList()
    val pointIndexesToRemove = mutableListOf<Int>()
    for (i in 0 until itemCount) {
        val x = getX(i).toFloat()
        val y = getY(i).toFloat()
        val currentPoint = PointF(x, y)
        val isPointExist = pointsToAdd.remove(currentPoint)
        if (!isPointExist) pointIndexesToRemove.add(i)
    }
    for (pointIndex in pointIndexesToRemove.reversed()) {
        remove(pointIndex)
    }
    for (point in pointsToAdd) {
        add(point.x.toDouble(), point.y.toDouble())
    }
}

fun Context.showOnOffDialog(init: (LayoutDialogOnOffBinding) -> Unit, okClick: (LayoutDialogOnOffBinding, DialogInterface) -> Unit): AlertDialog = AlertDialog.Builder(this).let {
    val inflater = LayoutInflater.from(this)
    val binding = LayoutDialogOnOffBinding.inflate(inflater)
    init(binding)
    val dialog = it.apply {
        setTitle("Set On/Off commands")
        setView(binding.root)
        setPositiveButton(R.string.ui_save) { dialog, _ ->
            dialog.hideSoftInput()
            okClick(binding, dialog)
        }
        setNegativeButton(R.string.ui_cancel) { dialog, _ ->
            dialog.hideSoftInput()
            dialog.cancel()
        }
    }.create()
    dialog.show()
    dialog
}

private fun DialogInterface.hideSoftInput() {
    (this as? AlertDialog)?.window?.let {
        WindowInsetsControllerCompat(it, it.decorView).hide(WindowInsetsCompat.Type.ime())
    }
}

fun TextView.append(command: Command) {
    val countLines = 30
    text = (command.toString() + text.toString()).split(Pattern.compile("\\n"), limit = countLines).mapIndexed { index, s ->
        if (index != countLines - 1) {
            s
        } else {
            val index = s.indexOf("\n")
            if (index != -1) {
                s.substring(0, index)
            } else {
                s
            }
        }
    }.joinToString(separator = "\n")
}

fun View.showMeasureDialog(init: (LayoutDialogMeasureBinding) -> Unit, okClick: (LayoutDialogMeasureBinding, DialogInterface) -> Unit): AlertDialog = AlertDialog.Builder(context).let {
    isEnabled = false
    val inflater = LayoutInflater.from(context)
    val binding = LayoutDialogMeasureBinding.inflate(inflater)
    init(binding)
    val dialog = it.apply {
        setTitle("Start Measure")
        setView(binding.root)
        setPositiveButton(R.string.ui_save) { dialog, _ ->
            dialog.hideSoftInput()
            okClick(binding, dialog)
        }
        setNegativeButton(R.string.ui_cancel) { dialog, _ ->
            dialog.hideSoftInput()
        }
    }.create()
    dialog.setOnCancelListener { isEnabled = true }
    dialog.show()
    dialog
}