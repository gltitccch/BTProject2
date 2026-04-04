package com.example.btproject2.ui.activities

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.btproject2.R

class RelationshipTypesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relationship_types)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
    }
}