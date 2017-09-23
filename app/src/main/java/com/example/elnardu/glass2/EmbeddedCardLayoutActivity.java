/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.elnardu.glass2;

import com.example.elnardu.glass2.R;
import com.google.android.glass.content.Intents;
import com.google.android.glass.widget.CardScrollView;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.FileObserver;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * Creates a card scroll view that shows an example of using a custom embedded layout in a
 * {@code CardBuilder}.
 */
public final class EmbeddedCardLayoutActivity extends Activity {

    private CardScrollView mCardScroller;
    private Socket socket;
    private Camera mCamera;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        try {
            socket = IO.socket("http://54.209.243.70:8080/");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                socket.emit("echo", "hi");
//                socket.disconnect();
            }

        }).on("event", new Emitter.Listener() {

            @Override
            public void call(Object... args) {}

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {}

        });
        socket.connect();
        mCardScroller = new CardScrollView(this);
        mCardScroller.setAdapter(new EmbeddedCardLayoutAdapter(this, createItems()));
        setContentView(mCardScroller);
        takePicture();

    }

    /** Creates some sample items that will be displayed on cards in the card scroll view. */
    private List<SimpleTableItem> createItems() {
        ArrayList<SimpleTableItem> items = new ArrayList<SimpleTableItem>();

        items.add(new SimpleTableItem(R.drawable.ic_circle_blue, "Water", "8 oz"));
        items.add(new SimpleTableItem(R.drawable.ic_circle_yellow, "Eggs, large", "2"));
        items.add(new SimpleTableItem(R.drawable.ic_circle_red, "Ground beef", "4 oz"));
        items.add(new SimpleTableItem(R.drawable.ic_circle_green, "Brussel sprouts", "1 cup"));
        items.add(new SimpleTableItem(R.drawable.ic_circle_green, "Celery", "1 stalk"));
        items.add(new SimpleTableItem(R.drawable.ic_circle_red, "Beef jerky", "8 strips"));
        items.add(new SimpleTableItem(R.drawable.ic_circle_yellow, "Almonds", "3 handfuls"));
        items.add(new SimpleTableItem(
                R.drawable.ic_circle_red, "Strawberry fruit leather", "2.5 miles"));

        return items;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardScroller.activate();
    }

    @Override
    protected void onPause() {
        mCardScroller.deactivate();
        super.onPause();
    }


    private static final int TAKE_PICTURE_REQUEST = 1;

    private void takePicture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PICTURE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TAKE_PICTURE_REQUEST && resultCode == RESULT_OK) {
//            String thumbnailPath = data.getStringExtra(Intents.EXTRA_THUMBNAIL_FILE_PATH);
            String picturePath = data.getStringExtra(Intents.EXTRA_PICTURE_FILE_PATH);

            processPictureWhenReady(picturePath);
            // TODO: Show the thumbnail to the user while the full picture is being
            // processed.

        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void processPictureWhenReady(final String picturePath) {
        final File pictureFile = new File(picturePath);
        if (pictureFile.exists()) {
            System.out.println("PICTURE EXISTS");
            processPicture(pictureFile);
        } else {
            // The file does not exist yet. Before starting the file observer, you
            // can update your UI to let the user know that the application is
            // waiting for the picture (for example, by displaying the thumbnail
            // image and a progress indicator).

            final File parentDirectory = pictureFile.getParentFile();
            FileObserver observer = new FileObserver(parentDirectory.getPath(),
                    FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
                // Protect against additional pending events after CLOSE_WRITE
                // or MOVED_TO is handled.
                private boolean isFileWritten;

                @Override
                public void onEvent(int event, String path) {
                    if (!isFileWritten) {
                        // For safety, make sure that the file that was created in
                        // the directory is actually the one that we're expecting.
                        File affectedFile = new File(parentDirectory, path);
                        isFileWritten = affectedFile.equals(pictureFile);

                        if (isFileWritten) {
                            stopWatching();

                            // Now that the file is ready, recursively call
                            // processPictureWhenReady again (on the UI thread).
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    processPictureWhenReady(picturePath);
                                }
                            });
                        }
                    }
                }
            };
            observer.startWatching();
        }
    }

    private void processPicture(File file) {
        Bitmap bm = BitmapFactory.decodeFile(file.getPath());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos); //bm is the bitmap object
        byte[] b = baos.toByteArray();
        String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);

        socket.emit("analyzeFace", b);
    }
}
