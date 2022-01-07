package com.young.webrtc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.young.rtp.util.RTPLog
import com.young.util.Permission
import com.young.util.TouchEffect

class MainActivity : AppCompatActivity(), View.OnClickListener, View.OnTouchListener {
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setSupportActionBar(findViewById(R.id.toolbar))

        //SIP component
        //rest component
        val clRest = findViewById<ConstraintLayout>(R.id.cl_rest)
        clRest.visibility = VISIBLE

        val tvSignIn = findViewById<TextView>(R.id.tv_sign_in)
        val ivCall = findViewById<ImageView>(R.id.iv_rest_call)
        val ivMessage = findViewById<ImageView>(R.id.iv_rest_message)

        tvSignIn.setOnClickListener(this)
        tvSignIn.setOnTouchListener(this)
        ivCall.setOnClickListener(this)
        ivCall.setOnTouchListener(this)
        ivMessage.setOnClickListener(this)
        ivMessage.setOnTouchListener(this)
    }

    override fun onResume() {
        super.onResume()
        RTPLog.i(TAG, "onResume")
        checkPermissions()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivityForResult(intent, SETTING_REQUEST_CODE)
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.tv_sign_in -> signIn()
            R.id.iv_rest_call -> callRTCActivity()
            R.id.iv_rest_message -> callMessageActivity()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (v?.id) {
            R.id.tv_sign_in -> TouchEffect().tv(v, event)
            R.id.iv_rest_call -> TouchEffect().iv(v, event)
            R.id.iv_rest_message -> TouchEffect().iv(v, event)
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Permission.REQUEST_CODE && grantResults.isNotEmpty()) {
            var hasPermission = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    hasPermission = false
                    break
                }
            }
            RTPLog.i(TAG, "permission: $hasPermission")
        }
    }

    private fun checkPermissions() {
        if (!Permission.check(this, arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO))) {
            Permission.request(this, arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun signIn() {

    }

    private fun callRTCActivity() {

    }

    private fun callMessageActivity() {

    }

    companion object {
        private const val TAG = "MainActivity"
        private const val SETTING_REQUEST_CODE = 487
    }
}