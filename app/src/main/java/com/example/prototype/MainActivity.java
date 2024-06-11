package com.example.prototype;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private Button checkConnectionButton;
    private static String ADDRESS;
    private static String POST_ADD_REQUEST;
    private static String GET_QUEUE_REQUEST;
    private static String DELETE_TRACK_REQUEST;
    private static String POST_RATE_REQUEST;
    private ArrayList<MusicTrack> musicList;
    private MusicAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String artist;
    private String trackName;
    private String duration;
    private ImageButton download;
    private static final int PICK_AUDIO_REQUEST = 1;
    private com.example.prototype.databinding.ActivityMainBinding binding;
    private MediaPlayer mediaPlayer;
    private int currentTrackIndex = -1; // Index of the currently playing track

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = com.example.prototype.databinding.ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        showServerAddressDialog();
        download = binding.imageButton2;
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileChooser();
            }
        });
        RecyclerView recyclerView = binding.recyclerview;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        musicList = new ArrayList<>();
        adapter = new MusicAdapter(musicList);
        recyclerView.setAdapter(adapter);

        // Initialize MediaPlayer
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                onTrackCompletion();
            }
        });
        startQueueUpdate();
    }
    private void startQueueUpdate() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Запрос на получение очереди треков
                new GetQueueTask(MainActivity.this).execute(GET_QUEUE_REQUEST);
                // Повторение запроса каждые 5 секунд
                handler.postDelayed(this, 20000);
            }
        }, 20000);
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri audioUri = data.getData();
            if (audioUri != null) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(this, audioUri);

                    trackName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

                    // Проверка на наличие данных и установка значения "Unknown" при отсутствии
                    trackName = (trackName != null && !trackName.isEmpty()) ? trackName : "Unknown";
                    artist = (artist != null && !artist.isEmpty()) ? artist : "Unknown";

                    // Преобразование длительности в формат минута:секунда
                    long durationInMillis = Long.parseLong(duration);
                    String formattedDuration = String.format("%02d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(durationInMillis),
                            TimeUnit.MILLISECONDS.toSeconds(durationInMillis) -
                                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(durationInMillis)));

                    addMusicTrack(artist, trackName, formattedDuration, audioUri);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Обработка ошибки, если не удалось получить данные о треке
                } finally {
                    try {
                        retriever.release();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
            }
        }
    }

    private void showServerAddressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Введите адрес сервера");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Проверить", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String serverAddress = input.getText().toString();
                if (!serverAddress.isEmpty()) {
                    // Ensure the URL is correctly formatted
                    if (!serverAddress.startsWith("http://") && !serverAddress.startsWith("https://")) {
                        serverAddress = "http://" + serverAddress;
                    }
                    ADDRESS = serverAddress;
                    new CheckConnectionTask(MainActivity.this).execute(serverAddress);
                } else {
                    Toast.makeText(MainActivity.this, "Введите адрес сервера", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                // Exit the application if the user cancels
                finish();
            }
        });

        builder.show();
    }
    private static class CheckConnectionTask extends AsyncTask<String, Void, String> {
        private Context context;

        public CheckConnectionTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... params) {
            String serverAddress = params[0];
            String response = "";

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(serverAddress)
                    .build();

            try {
                Response httpResponse = client.newCall(request).execute();
                if (httpResponse.isSuccessful()) {
                    response = "Успешное подключение";
                    POST_ADD_REQUEST = ADDRESS + "/tracks/add";
                    GET_QUEUE_REQUEST = ADDRESS + "/tracks/queue";
                    POST_RATE_REQUEST = ADDRESS + "/tracks/rate";
                    DELETE_TRACK_REQUEST = ADDRESS + "/tracks/remove";
                } else {
                    response = "Не удалось подключиться к серверу. Код ответа: " + httpResponse.code();
                }
            } catch (IOException e) {
                e.printStackTrace();
                response = "Ошибка подключения: " + e.getMessage();
            }

            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(context, result, Toast.LENGTH_LONG).show();
            if (result.equals("Успешное подключение")) {
                new GetQueueTask(context).execute(GET_QUEUE_REQUEST);
            }
        }
    }
    private static class GetQueueTask extends AsyncTask<String, Void, ArrayList<MusicTrack>> {
        private Context context;

        public GetQueueTask(Context context) {
            this.context = context;
        }

        @Override
        protected ArrayList<MusicTrack> doInBackground(String... params) {
            String getQueueUrl = params[0];
            ArrayList<MusicTrack> queue = new ArrayList<>();

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(getQueueUrl)
                    .build();

            try {
                Response httpResponse = client.newCall(request).execute();
                if (httpResponse.isSuccessful()) {
                    String responseBody = httpResponse.body().string();
                    JSONArray jsonArray = new JSONArray(responseBody);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject trackJson = jsonArray.getJSONObject(i);
                        String artist = trackJson.getString("artist");
                        String trackName = trackJson.getString("trackName");
                        String duration = trackJson.getString("duration");
                        Uri audioUri = Uri.parse(trackJson.getString("audioUri"));
                        queue.add(new MusicTrack(artist, trackName, duration, audioUri));
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }

            return queue;
        }

        @Override
        protected void onPostExecute(ArrayList<MusicTrack> queue) {
            MainActivity activity = (MainActivity) context;
            activity.updateMusicQueue(queue);
        }
    }


    // Метод для обновления очереди
    public void updateMusicQueue(ArrayList<MusicTrack> serverQueue) {
        // Удаляем из локальной очереди треки, которых нет в серверной очереди
        for (int i = 0; i < musicList.size(); i++) {
            MusicTrack localTrack = musicList.get(i);
            boolean existsInServerQueue = false;
            for (MusicTrack serverTrack : serverQueue) {
                if (localTrack.getAudioUri().equals(serverTrack.getAudioUri())) {
                    existsInServerQueue = true;
                    break;
                }
            }
            if (!existsInServerQueue) {
                musicList.remove(i);
                i--; // уменьшаем индекс после удаления
            }
        }

        // Добавляем в локальную очередь треки, которые есть на сервере, но отсутствуют в локальной очереди
        for (MusicTrack serverTrack : serverQueue) {
            boolean existsInLocalQueue = false;
            for (MusicTrack localTrack : musicList) {
                if (serverTrack.getAudioUri().equals(localTrack.getAudioUri())) {
                    existsInLocalQueue = true;
                    break;
                }
            }
            if (!existsInLocalQueue) {
                musicList.add(serverTrack);
            }
        }

        adapter.notifyDataSetChanged(); // Обновляем RecyclerView
        if (!musicList.isEmpty() && !mediaPlayer.isPlaying()) {
            playNextTrack();
        }
    }



    // Method to send track information to the server
    private void sendTrackToServer(String artist, String trackName, String duration, Uri audioUri) {
        new SendTrackTask(this, artist, trackName, duration, audioUri).execute(POST_ADD_REQUEST);
    }

    private static class SendTrackTask extends AsyncTask<String, Void, String> {
        private Context context;
        private String artist;
        private String trackName;
        private String duration;
        private Uri audioUri;

        public SendTrackTask(Context context, String artist, String trackName, String duration, Uri audioUri) {
            this.context = context;
            this.artist = artist;
            this.trackName = trackName;
            this.duration = duration;
            this.audioUri = audioUri;
        }
        @Override
        protected String doInBackground(String... params) {
            String postAddUrl = params[0];
            String response = "";

            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.get("application/json; charset=utf-8");

            // Create JSON payload
            String jsonPayload = String.format("{\"artist\":\"%s\", \"trackName\":\"%s\", \"duration\":\"%s\", \"audioUri\":\"%s\"}",
                    artist, trackName, duration, audioUri.toString());

            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder()
                    .url(postAddUrl)
                    .post(body)
                    .build();

            try {
                Response httpResponse = client.newCall(request).execute();
                if (httpResponse.isSuccessful()) {
                    response = "Трек успешно отправлен на сервер";
                } else {
                    response = "Не удалось отправить трек на сервер. Код ответа: " + httpResponse.code();
                }
            } catch (IOException e) {
                e.printStackTrace();
                response = "Ошибка отправки трека: " + e.getMessage();
            }

            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(context, result, Toast.LENGTH_LONG).show();
        }
    }

    // Метод для добавления трека в список и отправки его на сервер
    private void addMusicTrack(String artist, String trackName, String duration, Uri audioUri) {
        MusicTrack musicTrack = new MusicTrack(artist, trackName, duration, audioUri);
        musicList.add(musicTrack);
        adapter.notifyDataSetChanged(); // Уведомляем адаптер о том, что данные изменились
        sendTrackToServer(artist, trackName, duration, audioUri); // Отправка трека на сервер
        if (musicList.size() == 1) {
            // Start playing the first track immediately
            playNextTrack();
        }
    }

    private void playNextTrack() {
        if (!musicList.isEmpty()) {
            currentTrackIndex = 0;
            MusicTrack track = musicList.get(currentTrackIndex);
            try {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(this, track.getAudioUri());
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Ошибка воспроизведения трека", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void onTrackCompletion() {
        if (currentTrackIndex != -1 && currentTrackIndex < musicList.size()) {
            MusicTrack completedTrack = musicList.get(currentTrackIndex); // Получаем завершенный трек

            // Удаление трека с сервера
            deleteTrackFromServer(completedTrack.getAudioUri());

            // Удаляем завершенный трек из списка
            musicList.remove(currentTrackIndex);
            adapter.notifyDataSetChanged(); // Обновляем RecyclerView

            if (!musicList.isEmpty()) {
                // Воспроизводим следующий трек
                playNextTrack();
            } else {
                // Нет треков для воспроизведения
                currentTrackIndex = -1;
            }
        }
    }

    private void deleteTrackFromServer(Uri audioUri) {
        new DeleteTrackTask(this, audioUri).execute(DELETE_TRACK_REQUEST);
    }

    private static class DeleteTrackTask extends AsyncTask<String, Void, String> {
        private Context context;
        private Uri audioUri;

        public DeleteTrackTask(Context context, Uri audioUri) {
            this.context = context;
            this.audioUri = audioUri;
        }

        @Override
        protected String doInBackground(String... params) {
            String deleteUrl = params[0];
            String response = "";

            OkHttpClient client = new OkHttpClient();

            // Используем DELETE-запрос
            Request request = new Request.Builder()
                    .url(deleteUrl)
                    .delete() // Указываем, что это DELETE-запрос
                    .build();

            try {
                Response httpResponse = client.newCall(request).execute();
                if (httpResponse.isSuccessful()) {
                    response = "Трек успешно удален с сервера";
                } else {
                    response = "Не удалось удалить трек с сервера. Код ответа: " + httpResponse.code();
                }
            } catch (IOException e) {
                e.printStackTrace();
                response = "Ошибка удаления трека: " + e.getMessage();
            }

            return response;
        }

        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(context, result, Toast.LENGTH_LONG).show();
        }
    }

}
