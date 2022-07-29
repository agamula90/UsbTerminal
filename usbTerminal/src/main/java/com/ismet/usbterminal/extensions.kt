package com.ismet.usbterminal

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.PointF
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.LayoutDialogOnOffBinding
import org.achartengine.model.XYSeries

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