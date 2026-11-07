package ru.hck.voice;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Button btnSelectFile;
    private TextView tvStatus;
    private TextView tvResult;
    private ProgressBar progressBar;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean pipelineReady = false;

    // Пути к моделям в assets
    private static final String MODELS_DIR = "models";
    private static final String SHERPA_RU_DIR = MODELS_DIR + "/sherpa-ru";
    private static final String SILERO_VAD_PATH = MODELS_DIR + "/silero_vad.onnx";
    private static final String WESPEAKER_PATH = MODELS_DIR + "/wespeaker.onnx";

    private ActivityResultLauncher<Intent> pickFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSelectFile = findViewById(R.id.btnSelectFile);
        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        progressBar = findViewById(R.id.progressBar);

        // Регистрация launcher'а для выбора файла
        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handlePickedFile(uri);
                        }
                    }
                }
        );

        btnSelectFile.setEnabled(false);
        btnSelectFile.setOnClickListener(v -> openFilePicker());

        // Инициализация Python и прогрев пайплайна в фоне
        executor.execute(() -> {
            try {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(MainActivity.this));
                }
                Python py = Python.getInstance();
                PyObject module = py.getModule("pipeline_bridge");

                // Вызываем Python-функцию warmup, которая создаёт пайплайн
                // и загружает модели в память
                module.callAttr("warmup", SHERPA_RU_DIR, SILERO_VAD_PATH, WESPEAKER_PATH);

                pipelineReady = true;
                mainHandler.post(() -> {
                    tvStatus.setText("Модели загружены. Выберите WAV-файл.");
                    btnSelectFile.setEnabled(true);
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        tvStatus.setText("Ошибка загрузки моделей: " + e.getMessage()));
            }
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        pickFileLauncher.launch(intent);
    }

    private void handlePickedFile(Uri uri) {
        if (!pipelineReady) {
            Toast.makeText(this, "Модели ещё загружаются", Toast.LENGTH_SHORT).show();
            return;
        }
        btnSelectFile.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Транскрибация…");
        tvResult.setText("");

        executor.execute(() -> {
            File tempFile = null;
            try {
                // 1. Копируем выбранный файл во временный файл,
                //    чтобы Python мог читать его по обычному пути
                tempFile = copyUriToTempFile(uri);

                // 2. Вызываем Python-функцию транскрибации
                Python py = Python.getInstance();
                PyObject module = py.getModule("pipeline_bridge");
                String result = module.callAttr("run_file", tempFile.getAbsolutePath()).toString();

                // 3. Показываем результат
                final String finalResult = result;
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSelectFile.setEnabled(true);
                    tvStatus.setText("Готово");
                    tvResult.setText(finalResult);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSelectFile.setEnabled(true);
                    tvStatus.setText("Ошибка: " + e.getMessage());
                });
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        });
    }

    private File copyUriToTempFile(Uri uri) throws Exception {
        String displayName = null;
        Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }
        if (displayName == null) {
            displayName = "input.wav";
        }

        File tempFile = new File(getCacheDir(), displayName);
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(tempFile)) {
            if (input == null) {
                throw new IllegalStateException("Не удалось открыть файл");
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) > 0) {
                output.write(buffer, 0, len);
            }
        }
        return tempFile;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}