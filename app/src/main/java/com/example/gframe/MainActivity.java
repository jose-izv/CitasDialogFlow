package com.example.gframe;

import static android.speech.tts.TextToSpeech.SUCCESS;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> sttLauncher;
    private Intent sttIntent;
    EditText etDialogFlow;
    TextView tvResponseDialogFlow;
    Button btDialogFlow;
    private SessionsClient sessionClient;
    private SessionName sessionName;
    private final static String SESSION_UUID = UUID.randomUUID().toString();
    boolean ttsReady = false;
    TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sttLauncher = getSttLauncher();
        sttIntent = getSttIntent();

        init();
    }

    private void init(){
        etDialogFlow = findViewById(R.id.etDialogFlow);
        tvResponseDialogFlow = findViewById(R.id.tvResponseDialogFlow);
        btDialogFlow = findViewById(R.id.btDialogFlow);

        tts = new TextToSpeech(this,status -> {
            if(status == SUCCESS){
                ttsReady = true;
                tts.setLanguage(new Locale("spa","ES"));
            }
        });

        if(setupDialogflowClient()){
            btDialogFlow.setOnClickListener(v -> {
                sendToDialogFlow();
            });
        }
    }

    private Boolean setupDialogflowClient() {
        Boolean isSetup = Boolean.FALSE;
        try {
            InputStream stream = this.getResources().openRawResource(R.raw.client);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
                    FixedCredentialsProvider.create(credentials)).build();
            sessionClient = SessionsClient.create(sessionsSettings);
            sessionName = SessionName.of(projectId, SESSION_UUID);
            isSetup = Boolean.TRUE;
        } catch (Exception e) {
            showMessage("\nexception in setupBot: " + e.getMessage() + "\n");
        }
        return isSetup;
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            tvResponseDialogFlow.append(message + tvResponseDialogFlow.getText().toString() + ".\n");
        });
    }

    private void sendToDialogFlow(){
        String inputText = etDialogFlow.getText().toString();
        etDialogFlow.setText("");
        if(inputText.isEmpty()){
            sttLauncher.launch(sttIntent);
        }else {
            sendMessageToBot(inputText);
        }
    }

    private void sendMessageToBot(String message) {
        QueryInput input = QueryInput.newBuilder().setText(
                TextInput.newBuilder().setText(message).setLanguageCode("es-ES")).build();
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    DetectIntentRequest detectIntentRequest =
                            DetectIntentRequest.newBuilder()
                                    .setSession(sessionName.toString())
                                    .setQueryInput(input)
                                    .build();
                    DetectIntentResponse detectIntentResponse = sessionClient.detectIntent(detectIntentRequest);
                    if(detectIntentResponse != null) {

                        //intent, action, sentiment
                        String action = detectIntentResponse.getQueryResult().getAction();
                        String intent = detectIntentResponse.getQueryResult().getIntent().toString();
                        String sentiment = detectIntentResponse.getQueryResult().getSentimentAnalysisResult().toString();

                        String botReply = detectIntentResponse.getQueryResult().getFulfillmentText();
                        if(!botReply.isEmpty()) {
                            showAndSpeakResult(botReply + "\n");
                        } else {
                            showMessage("something went wrong\n");
                        }
                    } else {
                        showMessage("connection failed\n");
                    }
                } catch (Exception e) {
                    showMessage("\nexception in thread: " + e.getMessage() + "\n");
                    e.printStackTrace();
                }
            }
        };
        thread.start();
        System.out.println("Lanza el hilo.");
    }
    private Intent getSttIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("spa", "ES"));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla ahora...");
        return intent;
    }

    private ActivityResultLauncher<Intent> getSttLauncher() {
        return registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    String text = "Ups...";
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        List<String> r = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        text = r.get(0);
                    } else if(result.getResultCode() == Activity.RESULT_CANCELED) {
                        text = "Error...";
                    }
                    sendMessageToBot(text);
                }
        );
    }

    private void showAndSpeakResult(String result) {
        tvResponseDialogFlow.append(result + ".\n");
        if(ttsReady){
            tts.speak(result,TextToSpeech.QUEUE_ADD,null,null);
        }
    }

}