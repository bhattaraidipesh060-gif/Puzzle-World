package com.jeiu.puzzleworld

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Scrollable Terms of Service &amp; Privacy Policy page.
 * Content lives in strings.xml for easy editing.
 */
class TermsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)
    }
}