package com.wenkesj.voice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.Nullable
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext.RCTDeviceEventEmitter
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.PermissionAwareActivity
import java.util.Locale


class Voice (context:ReactApplicationContext):RecognitionListener, ActivityEventListener {
  val reactContext: ReactApplicationContext = context
  private var speech: SpeechRecognizer? = null
  private var isRecognizing = false
  private var locale: String? = null
  private var useUIMode = false
  private var pendingCallback: Callback? = null
  
  companion object {
    private const val REQUEST_SPEECH_RECOGNIZER = 1001
  }

  init {
    reactContext.addActivityEventListener(this)
  }

  private fun getLocale(locale: String?): String {
    if (locale != null && locale != "") {
      return locale
    }
    return Locale.getDefault().toString()
  }

  private fun startListening(opts: ReadableMap) {
    if (speech != null) {
      speech?.destroy()
      speech = null
    }

    speech = if (opts.hasKey("RECOGNIZER_ENGINE")) {
      when (opts.getString("RECOGNIZER_ENGINE")) {
        "GOOGLE" -> {
          SpeechRecognizer.createSpeechRecognizer(
            this.reactContext,
            ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService")
          )
        }

        else -> SpeechRecognizer.createSpeechRecognizer(this.reactContext)
      }
    } else {
      SpeechRecognizer.createSpeechRecognizer(this.reactContext)
    }

    speech?.setRecognitionListener(this)

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

    // Load the intent with options from JS
    val iterator = opts.keySetIterator()
    while (iterator.hasNextKey()) {
      val key = iterator.nextKey()
      when (key) {
        "EXTRA_LANGUAGE_MODEL" -> when (opts.getString(key)) {
          "LANGUAGE_MODEL_FREE_FORM" -> intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
          )

          "LANGUAGE_MODEL_WEB_SEARCH" -> intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
          )

          else -> intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
          )
        }

        "EXTRA_MAX_RESULTS" -> {
          val extras = opts.getDouble(key)
          intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, extras.toInt())
        }

        "EXTRA_PARTIAL_RESULTS" -> {
          intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, opts.getBoolean(key))
        }

        "EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS" -> {
          val extras = opts.getDouble(key)
          intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, extras.toInt())
        }

        "EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS" -> {
          val extras = opts.getDouble(key)
          intent.putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            extras.toInt()
          )
        }

        "EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS" -> {
          val extras = opts.getDouble(key)
          intent.putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            extras.toInt()
          )
        }
      }
    }

    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocale(this.locale))
    speech?.startListening(intent)
  }

  private fun startListeningWithUI(opts: ReadableMap) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    
    // Load the intent with options from JS
    val iterator = opts.keySetIterator()
    while (iterator.hasNextKey()) {
      val key = iterator.nextKey()
      when (key) {
        "EXTRA_LANGUAGE_MODEL" -> when (opts.getString(key)) {
          "LANGUAGE_MODEL_FREE_FORM" -> intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
          )
          "LANGUAGE_MODEL_WEB_SEARCH" -> intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH
          )
          else -> intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
          )
        }
        "EXTRA_MAX_RESULTS" -> {
          val extras = opts.getDouble(key)
          intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, extras.toInt())
        }
        "EXTRA_PROMPT" -> {
          intent.putExtra(RecognizerIntent.EXTRA_PROMPT, opts.getString(key))
        }
      }
    }

    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocale(this.locale))
    
    val currentActivity = reactContext.currentActivity
    if (currentActivity != null) {
      currentActivity.startActivityForResult(intent, REQUEST_SPEECH_RECOGNIZER)
    } else {
      pendingCallback?.invoke("No current activity available")
      pendingCallback = null
    }
  }

  private fun startSpeechWithPermissions(locale: String, opts: ReadableMap, callback: Callback) {
    this.locale = locale
    
    // Check if UI mode is requested
    useUIMode = opts.hasKey("SHOW_UI") && opts.getBoolean("SHOW_UI")

    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        if (useUIMode) {
          pendingCallback = callback
          startListeningWithUI(opts)
          isRecognizing = true
          // Don't invoke callback here - wait for activity result
        } else {
          startListening(opts)
          isRecognizing = true
          callback.invoke(false)
        }
      } catch (e: Exception) {
        callback.invoke(e.message)
      }
    }
  }

  fun startSpeech(locale: String?, opts: ReadableMap, callback: Callback?) {
    if (!isPermissionGranted() && opts.getBoolean("REQUEST_PERMISSIONS_AUTO")) {
      val PERMISSIONS = arrayOf<String>(Manifest.permission.RECORD_AUDIO)
      if (reactContext.currentActivity != null) {
        (reactContext.currentActivity as PermissionAwareActivity).requestPermissions(
          PERMISSIONS, 1
        ) { requestCode, permissions, grantResults ->
          var permissionsGranted = true
          for (i in permissions.indices) {
            val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
            permissionsGranted = permissionsGranted && granted
          }
          startSpeechWithPermissions(locale!!, opts, callback!!)
          permissionsGranted
        }
      }
      return
    }
    startSpeechWithPermissions(locale!!, opts, callback!!)
  }


  fun stopSpeech(callback: Callback) {
    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        if (speech != null) {
          speech!!.stopListening()
        }
        isRecognizing = false
        callback.invoke(false)
      } catch (e: java.lang.Exception) {
        callback.invoke(e.message)
      }
    }
  }

  fun cancelSpeech(callback: Callback) {
    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        if (speech != null) {
          speech!!.cancel()
        }
        isRecognizing = false
        callback.invoke(false)
      } catch (e: java.lang.Exception) {
        callback.invoke(e.message)
      }
    }
  }

  fun destroySpeech(callback: Callback) {
    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        if (speech != null) {
          speech!!.destroy()
        }
        speech = null
        isRecognizing = false
        callback.invoke(false)
      } catch (e: java.lang.Exception) {
        callback.invoke(e.message)
      }
    }
  }

  fun isSpeechAvailable(callback: Callback) {
    val self: Voice = this
    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        val isSpeechAvailable = SpeechRecognizer.isRecognitionAvailable(self.reactContext)
        callback.invoke(isSpeechAvailable, false)
      } catch (e: java.lang.Exception) {
        callback.invoke(false, e.message)
      }
    }
  }

  fun getSpeechRecognitionServices(promise: Promise) {
    val services = reactContext.packageManager
      .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
    val serviceNames = Arguments.createArray()
    for (service in services) {
      serviceNames.pushString(service.serviceInfo.packageName)
    }

    promise.resolve(serviceNames)
  }

  private fun isPermissionGranted(): Boolean {
    val permission = Manifest.permission.RECORD_AUDIO
    val res: Int = reactContext.checkCallingOrSelfPermission(permission)
    return res == PackageManager.PERMISSION_GRANTED
  }

  fun isRecognizing(callback: Callback) {
    callback.invoke(isRecognizing)
  }

  // Transcription methods - placeholder implementations
  fun destroyTranscription(callback: Callback) {
    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        // TODO: Implement transcription destroy logic
        callback.invoke(false)
      } catch (e: Exception) {
        callback.invoke(e.message)
      }
    }
  }

  fun startTranscription(url: String, locale: String, opts: ReadableMap, callback: Callback) {
    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        // TODO: Implement transcription start logic
        callback.invoke(false)
      } catch (e: Exception) {
        callback.invoke(e.message)
      }
    }
  }

  fun stopTranscription(callback: Callback) {
    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        // TODO: Implement transcription stop logic
        callback.invoke(false)
      } catch (e: Exception) {
        callback.invoke(e.message)
      }
    }
  }

  fun cancelTranscription(callback: Callback) {
    val mainHandler = Handler(reactContext.mainLooper)
    mainHandler.post {
      try {
        // TODO: Implement transcription cancel logic
        callback.invoke(false)
      } catch (e: Exception) {
        callback.invoke(e.message)
      }
    }
  }

  private fun sendEvent(eventName: String, params: WritableMap) {
    reactContext
      .getJSModule(RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private fun getErrorText(errorCode: Int): String {
    val message = when (errorCode) {
      SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
      SpeechRecognizer.ERROR_CLIENT -> "Client side error"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
      SpeechRecognizer.ERROR_NETWORK -> "Network error"
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
      SpeechRecognizer.ERROR_NO_MATCH -> "No match"
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
      SpeechRecognizer.ERROR_SERVER -> "error from server"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
      else -> "Didn't understand, please try again."
    }
    return message
  }


  override fun onReadyForSpeech(params: Bundle?) {
    val event = Arguments.createMap()
    event.putBoolean("error", false)
    sendEvent("onSpeechStart", event)
    Log.d("ASR", "onReadyForSpeech()")
  }

  override fun onBeginningOfSpeech() {
    val event = Arguments.createMap()
    event.putBoolean("error", false)
    sendEvent("onSpeechStart", event)
    Log.d("ASR", "onBeginningOfSpeech()")
  }

  override fun onRmsChanged(rmsdB: Float) {
    val event = Arguments.createMap()
    event.putDouble("value", rmsdB.toDouble())
    sendEvent("onSpeechVolumeChanged", event)
  }

  override fun onBufferReceived(buffer: ByteArray?) {
    val event = Arguments.createMap()
    event.putBoolean("error", false)
    sendEvent("onSpeechRecognized", event)
    Log.d("ASR", "onBufferReceived()")
  }

  override fun onEndOfSpeech() {
    val event = Arguments.createMap()
    event.putBoolean("error", false)
    sendEvent("onSpeechEnd", event)
    Log.d("ASR", "onEndOfSpeech()")
    isRecognizing = false
  }


  override fun onError(error: Int) {
    val errorMessage = String.format("%d/%s", error, getErrorText(error))
    val errorData = Arguments.createMap()
    errorData.putString("message", errorMessage)
    errorData.putString("code", java.lang.String.valueOf(errorMessage))
    val event = Arguments.createMap()
    event.putMap("error", errorData)
    sendEvent("onSpeechError", event)
    Log.d("ASR", "onError() - $errorMessage")
  }

  override fun onResults(results: Bundle?) {
    val arr = Arguments.createArray()

    val matches = results!!.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (matches != null) {
      for (result in matches) {
        arr.pushString(result)
      }
    }
    val event = Arguments.createMap()
    event.putArray("value", arr)
    sendEvent("onSpeechResults", event)
    Log.d("ASR", "onResults()")
  }

  override fun onPartialResults(partialResults: Bundle?) {
    val arr = Arguments.createArray()

    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    matches?.let {
      for (result in it) {
        arr.pushString(result)
      }
    }

    val event = Arguments.createMap()
    event.putArray("value", arr)
    sendEvent("onSpeechPartialResults", event)
  }

  override fun onEvent(eventType: Int, params: Bundle?) {
    TODO("Not yet implemented")
  }

  // ActivityEventListener methods
  override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_SPEECH_RECOGNIZER) {
      isRecognizing = false
      
      when (resultCode) {
        Activity.RESULT_OK -> {
          val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
          if (results != null && results.isNotEmpty()) {
            val arr = Arguments.createArray()
            for (result in results) {
              arr.pushString(result)
            }
            val event = Arguments.createMap()
            event.putArray("value", arr)
            sendEvent("onSpeechResults", event)
            
            pendingCallback?.invoke(false)
          } else {
            val errorData = Arguments.createMap()
            errorData.putString("message", "No speech results")
            errorData.putString("code", "NO_RESULTS")
            val event = Arguments.createMap()
            event.putMap("error", errorData)
            sendEvent("onSpeechError", event)
            
            pendingCallback?.invoke("No speech results")
          }
        }
        Activity.RESULT_CANCELED -> {
          val errorData = Arguments.createMap()
          errorData.putString("message", "Speech recognition cancelled")
          errorData.putString("code", "CANCELLED")
          val event = Arguments.createMap()
          event.putMap("error", errorData)
          sendEvent("onSpeechError", event)
          
          pendingCallback?.invoke("Speech recognition cancelled")
        }
        else -> {
          val errorData = Arguments.createMap()
          errorData.putString("message", "Speech recognition failed")
          errorData.putString("code", "FAILED")
          val event = Arguments.createMap()
          event.putMap("error", errorData)
          sendEvent("onSpeechError", event)
          
          pendingCallback?.invoke("Speech recognition failed")
        }
      }
      
      pendingCallback = null
    }
  }

  override fun onNewIntent(intent: Intent) {
    // Not needed for speech recognition
  }
}
