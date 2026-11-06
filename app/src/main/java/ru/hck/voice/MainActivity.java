package ru.hck.voice;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.hck.voice.bridge.database.TranscriptionBridge;

public class MainActivity extends Activity {

    private EditText audioPathInput;
    private EditText contentInput;
    private TextView resultText;
    private Button saveButton;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createInterface();

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(getApplicationContext()));
        }
    }

    private void createInterface() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TranscriptionBridge.initialize(getApplicationContext());

        audioPathInput = new EditText(this);
        audioPathInput.setHint("Путь к аудио");
        audioPathInput.setText("/test/audio.wav");

        contentInput = new EditText(this);
        contentInput.setHint("Текст транскрипции");
        contentInput.setText("Тестовая транскрипция из Python");

        saveButton = new Button(this);
        saveButton.setText("Создать транскрипцию");

        resultText = new TextView(this);
        resultText.setText("Ожидание...");

        layout.addView(audioPathInput);
        layout.addView(contentInput);
        layout.addView(saveButton);
        layout.addView(resultText);

        setContentView(layout);

        saveButton.setOnClickListener(v -> saveTranscription());
    }

    private void saveTranscription() {
        String audioPath = audioPathInput.getText().toString();
        String content = contentInput.getText().toString();

        saveButton.setEnabled(false);
        resultText.setText("Сохранение...");

        executor.execute(() -> {
            try {
                Python python = Python.getInstance();

                PyObject module = python.getModule("main");

                PyObject result = module.callAttr(
                        "create_transcription",
                        audioPath,
                        content
                );

                int id = result.toInt();

                runOnUiThread(() -> {
                    resultText.setText(
                            "Запись успешно создана!\nID: " + id
                    );
                    saveButton.setEnabled(true);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    resultText.setText(
                            "Ошибка:\n" + e.getMessage()
                    );
                    saveButton.setEnabled(true);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}