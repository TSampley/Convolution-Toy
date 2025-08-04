package com.taushsampley.convolution

import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setSupportActionBar(findViewById(R.id.toolbar))

//        fab.setOnClickListener { view ->
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                    .setAction("Action", null).show()
//        }

        val recyclerView = findViewById<RecyclerView>(R.id.recycyler_convolutions)
        recyclerView.layoutManager = LinearLayoutManager(applicationContext)
        recyclerView.adapter = ConvolutionAdapter()
    }

    class ConvolutionAdapter : Adapter<ConvolutionHolder>() {

        val layers = ArrayList<Any>()

        init {
            layers.add("Convolution0")
            layers.add("Convolution1")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConvolutionHolder {
            return ConvolutionHolder(TextView(parent.context))
        }

        override fun getItemCount(): Int {
            return layers.count()
        }

        override fun onBindViewHolder(holder: ConvolutionHolder, position: Int) {

            holder.onBind(layers[position])
        }
    }

    class ConvolutionHolder(private val textView : TextView) : RecyclerView.ViewHolder(textView) {

        fun onBind(layer : Any) {
            textView.text = layer.toString()
        }
    }

}
