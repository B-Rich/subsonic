package net.sourceforge.subsonic.android.activity;

import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import android.media.MediaPlayer;
import android.media.AudioManager;
import net.sourceforge.subsonic.android.R;
import net.sourceforge.subsonic.android.domain.MusicDirectory;
import net.sourceforge.subsonic.android.service.DownloadService;
import net.sourceforge.subsonic.android.service.MusicService;
import net.sourceforge.subsonic.android.service.MusicServiceFactory;
import net.sourceforge.subsonic.android.service.StreamService;
import net.sourceforge.subsonic.android.util.BackgroundTask;
import net.sourceforge.subsonic.android.util.Constants;
import net.sourceforge.subsonic.android.util.ImageLoader;
import net.sourceforge.subsonic.android.util.Util;
import net.sourceforge.subsonic.android.util.SimpleServiceBinder;

public class SelectAlbumActivity extends OptionsMenuActivity implements AdapterView.OnItemClickListener {

    private static final String TAG = SelectAlbumActivity.class.getSimpleName();
    private final DownloadServiceConnection downloadServiceConnection = new DownloadServiceConnection();
    private final StreamServiceConnection streamServiceConnection = new StreamServiceConnection();
    private ImageLoader imageLoader;
    private DownloadService downloadService;
    private StreamService streamService;
    private ListView entryList;
    private Button downloadButton;
    private Button playButton;
    private Button selectAllOrNoneButton;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_album);
        setTitle(getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_NAME));

        imageLoader = new ImageLoader();
        downloadButton = (Button) findViewById(R.id.select_album_download);
        playButton = (Button) findViewById(R.id.select_album_play);
        selectAllOrNoneButton = (Button) findViewById(R.id.select_album_selectallornone);
        entryList = (ListView) findViewById(R.id.select_album_entries);

        entryList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);// TODO:Specify in XML.
        entryList.setOnItemClickListener(this);

        selectAllOrNoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectAllOrNone();
            }
        });

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                download();
            }
        });

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play();
            }
        });

        bindService(new Intent(this, DownloadService.class), downloadServiceConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, StreamService.class), streamServiceConnection, Context.BIND_AUTO_CREATE);
        load();
    }

    private void load() {
        new BackgroundTask<MusicDirectory>(SelectAlbumActivity.this) {
            @Override
            protected MusicDirectory doInBackground() throws Throwable {
                MusicService musicService = MusicServiceFactory.getMusicService();
                String path = getIntent().getStringExtra(Constants.INTENT_EXTRA_NAME_PATH);
                return musicService.getMusicDirectory(path, SelectAlbumActivity.this, this);
            }

            @Override
            protected void done(MusicDirectory result) {
                List<MusicDirectory.Entry> entries = result.getChildren();
                entryList.setAdapter(new EntryAdapter(entries));

                int visibility = View.GONE;
                for (MusicDirectory.Entry entry : entries) {
                    if (!entry.isDirectory()) {
                        visibility = View.VISIBLE;
                        break;
                    }
                }
                downloadButton.setVisibility(visibility);
                playButton.setVisibility(visibility);
                selectAllOrNoneButton.setVisibility(visibility);
            }

            @Override
            protected void cancel() {
                MusicServiceFactory.getMusicService().cancel(SelectAlbumActivity.this, this);
                finish();
            }
        }.execute();
    }

    private void selectAllOrNone() {
        boolean someUnselected = false;
        int count = entryList.getCount();
        for (int i = 0; i < count; i++) {
            if (!entryList.isItemChecked(i)) {
                someUnselected = true;
                break;
            }
        }
        selectAll(someUnselected);
    }

    private void selectAll(boolean selected) {
        int count = entryList.getCount();
        for (int i = 0; i < count; i++) {
            entryList.setItemChecked(i, selected);
        }
        enableDownloadButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(downloadServiceConnection);
        unbindService(streamServiceConnection);
        imageLoader.cancel();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position >= 0) {
            MusicDirectory.Entry entry = (MusicDirectory.Entry) parent.getItemAtPosition(position);
            Log.d(TAG, entry + " clicked.");
            if (entry.isDirectory()) {
                Intent intent = new Intent(this, SelectAlbumActivity.class);
                intent.putExtra(Constants.INTENT_EXTRA_NAME_PATH, entry.getId());
                intent.putExtra(Constants.INTENT_EXTRA_NAME_NAME, entry.getTitle());
                startActivity(intent);
            } else {
                enableDownloadButton();
            }
        }
    }

    private void enableDownloadButton() {
        int count = entryList.getCount();
        boolean checked = false;
        for (int i = 0; i < count; i++) {
            if (entryList.isItemChecked(i)) {
                checked = true;
                break;
            }
        }
        downloadButton.setEnabled(checked);
        playButton.setEnabled(checked);
    }

    private void download() {
        try {
            if (downloadService != null) {
                downloadService.download(getSelectedSongs());
                startActivity(new Intent(this, DownloadQueueActivity.class));
            } else {
                Log.e(TAG, "Not connected to Download Service.");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to contact Download Service.");
        }
    }

    private void play() {
        try {
            if (streamService != null) {
                streamService.add(getSelectedSongs(), false);
                startActivity(new Intent(this, StreamQueueActivity.class));
            } else {
                Log.e(TAG, "Not connected to Stream Service.");
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to contact Stream Service.");
        }
    }

    private List<MusicDirectory.Entry> getSelectedSongs() {
        List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>(10);
        int count = entryList.getCount();
        for (int i = 0; i < count; i++) {
            if (entryList.isItemChecked(i)) {
                songs.add((MusicDirectory.Entry) entryList.getItemAtPosition(i));
            }
        }
        return songs;
    }


    private class DownloadServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            downloadService = ((SimpleServiceBinder<DownloadService>) service).getService();
            Log.i(TAG, "Connected to Download Service");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            downloadService = null;
            Log.i(TAG, "Disconnected from Download Service");
        }
    }


    private class StreamServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            streamService = ((SimpleServiceBinder<StreamService>) service).getService();
            Log.i(TAG, "Connected to Stream Service");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            streamService = null;
            Log.i(TAG, "Disconnected from Stream Service");
        }
    }


    private class EntryAdapter extends ArrayAdapter<MusicDirectory.Entry> {
        public EntryAdapter(List<MusicDirectory.Entry> entries) {
            super(SelectAlbumActivity.this, android.R.layout.simple_list_item_1, entries);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MusicDirectory.Entry entry = getItem(position);
            TextView view;

            if (entry.isDirectory()) {
                view = (TextView) LayoutInflater.from(SelectAlbumActivity.this).inflate(
                        android.R.layout.simple_list_item_1, parent, false);

                view.setCompoundDrawablePadding(10);
                imageLoader.loadImage(view, entry);

            } else {
                if (convertView != null && convertView instanceof CheckedTextView) {
                    view = (TextView) convertView;
                } else {
                    view = (TextView) LayoutInflater.from(SelectAlbumActivity.this).inflate(
                            android.R.layout.simple_list_item_multiple_choice, parent, false);
                }
            }

            view.setText(entry.getTitle());

            return view;
        }
    }
}