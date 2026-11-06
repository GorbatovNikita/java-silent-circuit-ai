package ru.hck.voice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chaquo.python.Python;
import com.chaquo.python.PyObject;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 16000;
    private static final int REQUEST_MICROPHONE = 100;

    private Button startButton;
    private Button stopButton;
    private Button transcribeButton;

    private TextView statusText;
    private TextView resultText;
    private ProgressBar progressBar;

    private volatile boolean isRecording = false;

    private AudioRecord audioRecord;
    private Thread recordingThread;

    private File audioFile;

    private final ExecutorService transcriptionExecutor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        transcribeButton = findViewById(R.id.transcribeButton);

        statusText = findViewById(R.id.statusText);
        resultText = findViewById(R.id.resultText);
        progressBar = findViewById(R.id.progressBar);

        startButton.setOnClickListener(v -> startRecording());

        stopButton.setOnClickListener(v -> stopRecording());

        transcribeButton.setOnClickListener(v -> startTranscription());
    }

    // -------------------------
    // RECORDING
    // -------------------------

    private void startRecording() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MICROPHONE
            );

            return;
        }

        try {
            File recordingDir = new File(
                    getFilesDir(),
                    "recordings"
            );

            if (!recordingDir.exists() && !recordingDir.mkdirs()) {
                throw new IllegalStateException(
                        "Не удалось создать папку записей"
                );
            }

            audioFile = new File(
                    recordingDir,
                    "recording_" + System.currentTimeMillis() + ".wav"
            );

            int bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            if (bufferSize <= 0) {
                throw new IllegalStateException(
                        "Неподдерживаемая конфигурация AudioRecord"
                );
            }

            bufferSize = Math.max(bufferSize, SAMPLE_RATE);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release();
                audioRecord = null;

                throw new IllegalStateException(
                        "Не удалось инициализировать микрофон"
                );
            }

            audioRecord.startRecording();

            if (audioRecord.getRecordingState()
                    != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException(
                        "Не удалось начать запись"
                );
            }

            isRecording = true;

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            transcribeButton.setEnabled(false);

            statusText.setText("Идёт запись...");
            resultText.setText("");

            recordingThread = new Thread(
                    this::recordAudio,
                    "AudioRecordingThread"
            );

            recordingThread.start();

        } catch (Exception e) {
            isRecording = false;

            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }

            statusText.setText("Ошибка записи");
            startButton.setEnabled(true);
            stopButton.setEnabled(false);

            Toast.makeText(
                    this,
                    e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void recordAudio() {

        long totalBytes = 0;
        boolean success = false;

        try (FileOutputStream output =
                     new FileOutputStream(audioFile)) {

            // Резервируем место под WAV-заголовок.
            output.write(new byte[44]);

            byte[] buffer = new byte[4096];

            while (isRecording) {

                int read = audioRecord.read(
                        buffer,
                        0,
                        buffer.length
                );

                if (read > 0) {
                    output.write(buffer, 0, read);
                    totalBytes += read;
                } else if (read < 0) {
                    throw new IllegalStateException(
                            "Ошибка чтения аудио: " + read
                    );
                }
            }

            output.flush();

            writeWavHeader(audioFile, totalBytes);

            success = totalBytes > 0;

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                } catch (Exception ignored) {
                }

                audioRecord.release();
                audioRecord = null;
            }

            boolean finalSuccess = success;

            mainHandler.post(() -> {

                if (isFinishing() || isDestroyed()) return;

                startButton.setEnabled(true);
                stopButton.setEnabled(false);

                if (finalSuccess) {
                    transcribeButton.setEnabled(true);
                    statusText.setText("Запись сохранена");
                } else {
                    transcribeButton.setEnabled(false);
                    statusText.setText("Ошибка сохранения записи");
                }
            });
        }
    }

    private void stopRecording() {

        if (!isRecording) return;

        isRecording = false;

        statusText.setText("Сохранение записи...");

        stopButton.setEnabled(false);
    }

    // -------------------------
    // WAV HEADER
    // -------------------------

    private void writeWavHeader(File file, long audioBytes)
            throws Exception {

        long byteRate = SAMPLE_RATE * 2L;

        ByteBuffer header = ByteBuffer.allocate(44);
        header.order(ByteOrder.LITTLE_ENDIAN);

        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt((int) (36 + audioBytes));

        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});

        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);

        header.putInt(SAMPLE_RATE);
        header.putInt((int) byteRate);

        header.putShort((short) 2);
        header.putShort((short) 16);

        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt((int) audioBytes);

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(0);
            raf.write(header.array());
        }
    }

    // -------------------------
    // TRANSCRIPTION
    // -------------------------

    private void startTranscription() {

        if (audioFile == null || !audioFile.exists()) {
            Toast.makeText(
                    this,
                    "Запись не найдена",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        startButton.setEnabled(false);
        transcribeButton.setEnabled(false);

        progressBar.setVisibility(View.VISIBLE);

        statusText.setText("Загрузка Whisper и распознавание...");
        resultText.setText("");

        transcriptionExecutor.execute(() -> {

            try {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(
                            getApplicationContext()
                    ));
                }

                String modelPath = getModelPath();

                Python py = Python.getInstance();

                PyObject module = py.getModule(
                        "main"
                );

                PyObject result = module.callAttr(
                        "transcribe",
                        modelPath,
                        audioFile.getAbsolutePath()
                );

                String transcript = result.toString();

                mainHandler.post(() -> {

                    if (isFinishing() || isDestroyed()) return;

                    progressBar.setVisibility(View.GONE);

                    startButton.setEnabled(true);
                    transcribeButton.setEnabled(true);

                    statusText.setText("Распознавание завершено");

                    resultText.setText(
                            transcript.isEmpty()
                                    ? "Речь не распознана"
                                    : transcript
                    );
                });

            } catch (Exception e) {

                String error = e.toString();

                mainHandler.post(() -> {

                    if (isFinishing() || isDestroyed()) return;

                    progressBar.setVisibility(View.GONE);

                    startButton.setEnabled(true);
                    transcribeButton.setEnabled(true);

                    statusText.setText("Ошибка транскрибации");

                    resultText.setText(error);
                });
            }
        });
    }

    // -------------------------
    // MODEL FILE
    // -------------------------

    private String getModelPath() throws Exception {

        File modelDir = new File(
                getFilesDir(),
                "whisper"
        );

        if (!modelDir.exists() && !modelDir.mkdirs()) {
            throw new IllegalStateException(
                    "Не удалось создать папку модели"
            );
        }

        File modelFile = new File(
                modelDir,
                "ggml-base.bin"
        );

        if (!modelFile.exists()) {

            try (InputStream input = getAssets().open(
                    "whisper/ggml-base.bin"
            );

                 FileOutputStream output =
                         new FileOutputStream(modelFile)) {

                byte[] buffer = new byte[1024 * 1024];

                int count;

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }

                output.flush();
            }
        }

        return modelFile.getAbsolutePath();
    }

    // -------------------------
    // PERMISSIONS
    // -------------------------

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == REQUEST_MICROPHONE) {

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startRecording();

            } else {

                Toast.makeText(
                        this,
                        "Необходимо разрешение на микрофон",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // -------------------------
    // CLEANUP
    // -------------------------

    @Override
    protected void onDestroy() {

        isRecording = false;

        transcriptionExecutor.shutdown();

        super.onDestroy();
    }
}