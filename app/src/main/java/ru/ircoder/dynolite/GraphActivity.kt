package ru.ircoder.dynolite

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import ru.ircoder.dynolite.MainActivity.Companion.POINTS_X
import ru.ircoder.dynolite.MainActivity.Companion.POINTS_Y
import ru.ircoder.dynolite.databinding.ActivityGraphBinding

class GraphActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGraphBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGraphBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        val extras = intent.extras
        if (extras != null) {
            val pointsX = extras.getDoubleArray(POINTS_X)
            val pointsY = extras.getDoubleArray(POINTS_Y)
            // Snackbar.make(binding.graphView, "pointsX != null : ${pointsX != null} , pointsY != null : ${pointsY != null}", Snackbar.LENGTH_LONG).show()
            if (pointsX != null && pointsY != null && pointsX.isNotEmpty() && pointsY.isNotEmpty() && pointsX.size == pointsY.size) {
                // Snackbar.make(binding.graphView, "size : ${pointsX.size}", Snackbar.LENGTH_LONG).show()
                val points = Array(pointsX.size) { DataPoint(0.0, 0.0) }
                points.forEachIndexed { i: Int, _: DataPoint ->
                    points[i] = DataPoint(pointsX[i], pointsY[i])
                }
                val series = LineGraphSeries(points)
                binding.graphView.addSeries(series)
            }
        } // else Snackbar.make(binding.graphView, "extras is null", Snackbar.LENGTH_LONG).show()
    }
}