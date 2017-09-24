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
import android.view.KeyEvent;
import android.view.WindowManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import static android.content.ContentValues.TAG;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

/**
 * Creates a card scroll view that shows an example of using a custom embedded layout in a
 * {@code CardBuilder}.
 */
public final class EmbeddedCardLayoutActivity extends Activity {

    private CardScrollView mCardScroller;
    private Socket socket;
//    private Camera mCamera;
//    private CameraPreview mPreview;

//    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {
//
//        @Override
//        public void onPictureTaken(byte[] data, Camera camera) {
//
//            socket.emit("analyzeFace", data);

//            try {
//                FileOutputStream fos = new FileOutputStream(pictureFile);
//                fos.write(data);
//                fos.close();
//            } catch (FileNotFoundException e) {
//                Log.d(TAG, "File not found: " + e.getMessage());
//            } catch (IOException e) {
//                Log.d(TAG, "Error accessing file: " + e.getMessage());
//            }
//        }
//    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            if(socket == null) {
                socket = IO.socket("http://52.170.24.238:8080/");
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                socket.emit("echo", "hi");
//                socket.disconnect();
            }

        }).on("faceData", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                final JSONObject obj = (JSONObject)args[0];
                EmbeddedCardLayoutActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateTable(obj);
                    }
                });
            }

        }).on("noface", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                EmbeddedCardLayoutActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        noface();
                    }
                });
            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {}

        });
        socket.connect();
        mCardScroller = new CardScrollView(this);

//        mCamera = getCameraInstance();
        // Create our Preview view and set it as the content of our activity.
//        mPreview = new CameraPreview(this, mCamera);
//        takePicture();


    }


    private void updateTable(JSONObject obj) {
        ArrayList<SimpleTableItem> items = new ArrayList<SimpleTableItem>();

        try {
            items.add(new SimpleTableItem(R.drawable.ic_circle_blue, "Anger", obj.getDouble("anger")));
            items.add(new SimpleTableItem(R.drawable.ic_circle_yellow, "Contempt", obj.getDouble("contempt")));
            items.add(new SimpleTableItem(R.drawable.ic_circle_red, "Disgust", obj.getDouble("disgust")));
            items.add(new SimpleTableItem(R.drawable.ic_circle_green, "Fear", obj.getDouble("fear")));
            items.add(new SimpleTableItem(R.drawable.ic_circle_green, "Happiness", obj.getDouble("happiness")));
            items.add(new SimpleTableItem(R.drawable.ic_circle_red, "Neutral", obj.getDouble("neutral")));
            items.add(new SimpleTableItem(R.drawable.ic_circle_yellow, "Sadness", obj.getDouble("sadness")));
            items.add(new SimpleTableItem(R.drawable.ic_circle_red, "Surprise", obj.getDouble("surprise")));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Collections.sort(items);


        mCardScroller.setAdapter(new EmbeddedCardLayoutAdapter(this, items));
        setContentView(mCardScroller);
    }

    private void noface() {
        ArrayList<SimpleTableItem> items = new ArrayList<SimpleTableItem>();

        items.add(new SimpleTableItem(R.drawable.ic_circle_blue, "NO FACE", 0));

        mCardScroller.setAdapter(new EmbeddedCardLayoutAdapter(this, items));
        setContentView(mCardScroller);
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

    @Override
    protected void onDestroy() {
        socket.close();
        super.onDestroy();
    }

    private static final int TAKE_PICTURE_REQUEST = 1;

    private void takePicture() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PICTURE_REQUEST);
//        mCamera.takePicture(null, null, mPicture);
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
        bm = Bitmap.createScaledBitmap(bm, 632*2, 464*2, false);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos); //bm is the bitmap object
        byte[] b = baos.toByteArray();
//        String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);

        socket.emit("analyzeFace", b);
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            System.out.println(e);
        }
        return c; // returns null if camera is unavailable
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent event) {
        if (keycode == KeyEvent.KEYCODE_DPAD_CENTER) {
            takePicture();
            return true;
        }

        return super.onKeyDown(keycode, event);
    }
}
