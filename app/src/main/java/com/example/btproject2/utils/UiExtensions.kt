package com.example.btproject2.utils

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.example.btproject2.R

/**
 * Extension helper to configure Android Spinners with the Dark Forest & Warm Gold theme.
 */
fun Spinner.setDarkAdapter(context: Context, items: List<String>) {
    val adapter = ArrayAdapter(context, R.layout.item_spinner_dark, items).apply {
        setDropDownViewResource(R.layout.item_spinner_dropdown_dark)
    }
    this.adapter = adapter
}

