package com.example.audio_chat

import android.Manifest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import coil.compose.rememberAsyncImagePainter
import com.example.audio_chat.ui.theme.Audio_chatTheme
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.SpeechConfig
import com.microsoft.cognitiveservices.speech.SpeechRecognizer
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : ComponentActivity() {

    // Speech
    private val speechSubscriptionKey = "### KEY ####"
    private val serviceRegion = "southeastasia"

    private val speechConfig = SpeechConfig.fromSubscription(speechSubscriptionKey, serviceRegion)
    private val synthesizer = SpeechSynthesizer(speechConfig)
    private val recognizer = SpeechRecognizer(speechConfig)


    // Requesting permission to RECORD_AUDIO
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private var permissions: Array<String> = arrayOf(
        Manifest.permission.RECORD_AUDIO)


    //Message
    private var chatMessages : SnapshotStateList<Message> = mutableStateListOf()
    private var backendURL = "### URL ###"

    //Firebase
    private lateinit var auth: FirebaseAuth

    private val signInProviders = arrayListOf(
        AuthUI.IdpConfig.GoogleBuilder().build(),
    )

    private var userName = ""
    private var userIconURL = mutableStateOf("")

    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        this.onSignInResult(res)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Audio_chatTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        //Firebase
        auth = Firebase.auth
        setUserProfile()
    }

    private fun AddMessage(msg: String, isBot: Boolean = false) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val currentDateTime = LocalDateTime.now()

        chatMessages.add(
            Message(
                author = if(isBot) "#BOT" else userName,
                content = msg,
                timestamp = currentDateTime.format(formatter)
            )
        )
    }

    @Composable
    fun MainScreen() {
        val messages = remember { chatMessages }

        Scaffold(
            topBar = {
                CustomTopAppBar()
            },

            bottomBar = {
                CustomBottomAppBar()
            }

        ) { padding  ->
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(padding)) {
                MessageCardList(messages = messages)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CustomTopAppBar() {
        val iconPainter: Painter = rememberAsyncImagePainter(userIconURL.value)

        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),

            title = { Text("Chat") },

            actions = {
                IconButton(onClick = { signIn() }) {
                    if(userIconURL.value == "") {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Login"
                        )
                    }
                    else{
                        Image(
                            painter = iconPainter,
                            contentDescription = "User",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            },
        )
    }

    @Composable
    fun CustomBottomAppBar() {
        BottomAppBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            content = {
                IconButton(
                    modifier = Modifier.fillMaxSize(),
                    onClick = { onRecognizeButtonClicked() }) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Record",
                    )
                }
            }
        )
    }

    @Composable
    fun MessageCardList(messages: List<Message>) {
        val listState = rememberLazyListState()

        LaunchedEffect(messages.size) {
            listState.animateScrollToItem(messages.size)
        }
        LazyColumn (state = listState) {
            items(messages) { message ->
                MessageCard(message = message)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    @Composable
    fun MessageCard(message: Message) {
        val iconPainter: Painter = rememberAsyncImagePainter(userIconURL.value)

        val isBot = message.author == "#BOT"
        val align = if (isBot) Alignment.Start else Alignment.End

        Column(
            modifier = Modifier.fillMaxWidth().padding(all = 8.dp),
        ) {

            Row (modifier = Modifier.align(align)) {
                if(isBot) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "BOT",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                }
                else {
                    Image(
                        painter = iconPainter,
                        contentDescription = userName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = message.author,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = message.timestamp,
                        fontSize = 6.sp,
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(align)
                    .border(1.dp, Color.Gray, RoundedCornerShape(2.dp))
                    .padding(6.dp)
            )
        }
    }


    private fun onRecognizeButtonClicked() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "Please click the top right button to log in first.", Toast.LENGTH_SHORT).show()
            return
        }

        val task = recognizer.recognizeOnceAsync()
        val result = task.get()

        if (result.getReason() == ResultReason.RecognizedSpeech) {
            AddMessage(result.text)
            sendAI(result.text)
        }
        result.close()
    }

    private fun sendAI(message: String) {
        val client = OkHttpClient()

        val data = mapOf("message" to message)
        val json_data = Gson().toJson(data)
        val requestBody = json_data.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(backendURL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val answer = response.body?.string()
                    if (answer != null) {
                        Log.i("ai", answer)
                        AddMessage(answer, isBot = true)
                        startSpeech(answer)
                    }
                }
            }
        })
    }

    private fun startSpeech(text: String) {
        try {
            // Note: this will block the UI thread, so eventually, you want to register for the event
            val result = synthesizer.SpeakText(text)

            result.close()

        } catch (ex: Exception) {
            Log.e("SpeechSDKDemo", "unexpected " + ex.message)
        }
    }

    //
    private fun signIn() {
        if (auth.currentUser != null) return

        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(signInProviders)
            .build()
        signInLauncher.launch(signInIntent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            auth = Firebase.auth

            setUserProfile()
        }
    }

    private fun setUserProfile() {
        val user = auth.currentUser

        if (user != null) {
            Log.i("firebase", "setUserProfile")
            userName = user.displayName.toString()
            userIconURL.value = user.photoUrl.toString()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        synthesizer.close()
        recognizer.close()
        speechConfig.close()
    }


}
