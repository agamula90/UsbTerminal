package com.ismet.usbterminal.main.chart

import android.graphics.Color
import android.os.Bundle
import android.util.SparseArray
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ismet.usbterminal.format
import com.ismet.usbterminalnew.R
import com.ismet.usbterminalnew.databinding.FragmentChartBinding
import com.ismet.usbterminal.viewBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChartFragment : Fragment(R.layout.fragment_chart) {

    enum class DisplayValueType {
        TEXT_NUMBER,
        TEXT_PPM,
        TEXT_VALUE
    }

    private val binding by viewBinding(FragmentChartBinding::bind)
    private lateinit var ppmStrings: ArrayList<String>
    private lateinit var squareStrings: ArrayList<String>
    private lateinit var squares: SparseArray<Float>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        ppmStrings = args.getStringArrayList(PPMS_TAG)!!
        squareStrings = args.getStringArrayList(SQUARES_TAG)!!

        val countPoints: Int = squareStrings.size

        squares = SparseArray<Float>(countPoints)

        for (i in 0 until countPoints) {
            squares.put(ppmStrings[i].toInt(), squareStrings[i].toFloat())
        }

        binding.displayValues.configureDisplayValuesAdapter()
        binding.chart.configure(squares)
        binding.chart.invalidate()
    }

    private fun GridView.configureDisplayValuesAdapter() {
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = (squares.size() + 1) * DisplayValueType.values().size
            override fun getItem(position: Int): Any? = position.toDisplayValueType()

            private fun Int.toDisplayValueType(): DisplayValueType? = when {
                this.isHeader() -> null
                else -> DisplayValueType.values()[this % DisplayValueType.values().size]
            }

            private fun Int.isHeader() = this < DisplayValueType.values().size

            override fun getItemId(position: Int): Long {
                //+2 because in previous version value type ordinals differs from item id by +2
                return position.toDisplayValueType()?.ordinal?.toLong()?.let { it + 2 } ?: 0
            }

            override fun getView(position: Int, view: View?, parent: ViewGroup): View {
                var convertView = view
                val valueType = position.toDisplayValueType()
                if (convertView == null || convertView.tag != valueType) {
                    convertView = valueType.inflateView(parent)
                }
                convertView.tag = valueType
                convertView.layoutParams.height = convertView.resources
                    .getDimension(R.dimen.grid_view_curve_height).toInt()
                val textView = convertView.findViewById<View>(R.id.edit) as TextView

                when (valueType) {
                    null -> {
                        (convertView.findViewById<View>(R.id.header_name) as TextView).apply {
                            setBackgroundColor(R.color.edit_disabled.toColor())
                            text = resources.getStringArray(R.array.head1)[position]
                        }
                    }

                    else -> {
                        textView.apply {
                            isEnabled = false
                            gravity = Gravity.CENTER
                            setTextColor(Color.BLACK)
                            text = when (valueType) {
                                DisplayValueType.TEXT_NUMBER -> (position / DisplayValueType.values().size).toString()
                                DisplayValueType.TEXT_PPM -> squares.keyAt(position / DisplayValueType.values().size - 1)
                                    .toString()

                                DisplayValueType.TEXT_VALUE -> {
                                    squares.valueAt(
                                        position / DisplayValueType.values().size - 1
                                    ).format()
                                }
                            }

                            val textViewContainerBackgroundColor = when (valueType) {
                                DisplayValueType.TEXT_NUMBER -> R.color.edit_disabled
                                else -> R.color.grid_item_bkg_color
                            }
                            (this.parent as View).setBackgroundColor(
                                textViewContainerBackgroundColor.toColor()
                            )
                        }
                    }
                }
                return convertView
            }

            private fun DisplayValueType?.inflateView(parent: ViewGroup): View =
                LayoutInflater.from(activity).inflate(
                    when (this) {
                        null -> R.layout.layout_table_header
                        else -> R.layout.layout_table_edit_text
                    },
                    parent,
                    false
                )

            private fun Int.toColor() = ContextCompat.getColor(requireContext(), this)
        }
    }

    companion object {
        const val PPMS_TAG = "ppms"
        const val SQUARES_TAG = "squares"

        fun newInstance(ppmStrings: ArrayList<String>, squareStrings: ArrayList<String>) =
            ChartFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(PPMS_TAG, ppmStrings)
                    putStringArrayList(SQUARES_TAG, squareStrings)
                }
            }
    }
}