package com.olczyk.android.justarpaintingmarkerwithphoto;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.PixelCopy;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.DpToMetersViewSizer;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {

    private ArFragment arFragment;
    private FloatingActionButton photoFab;
    boolean shouldAddModel = true;
    float width;
    float height;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        arFragment = (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        arFragment.getPlaneDiscoveryController().hide();
        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
        photoFab = findViewById(R.id.photoFab);
        photoFab.setOnClickListener(view -> takePhoto());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void onUpdateFrame(FrameTime frameTime) {
        Frame frame = arFragment.getArSceneView().getArFrame();
        Collection<AugmentedImage> augmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);
        for (AugmentedImage augmentedImage : augmentedImages) {
            if (augmentedImage.getTrackingState() == TrackingState.TRACKING) {
                if (augmentedImage.getName().equals("tiger") && shouldAddModel) {
                    width = augmentedImage.getExtentX();
                    height = augmentedImage.getExtentZ();
                    placeImageView(arFragment,augmentedImage.createAnchor(augmentedImage.getCenterPose()));
                    shouldAddModel = false;
                }
            }
        }
    }

    private void placeImageView(ArFragment arFragment, Anchor anchor){
        CompletableFuture<Void> renderableFuture =
                ViewRenderable.builder()
                        .setView(arFragment.getContext(),R.layout.painting_module)
                        .setVerticalAlignment(ViewRenderable.VerticalAlignment.CENTER)
                        .build()
                        .thenAccept(renderable -> {
                            ImageView imageView = (ImageView) renderable.getView();
                            AnchorNode anchorNode = new AnchorNode(anchor);
                            anchorNode.setParent(arFragment.getArSceneView().getScene());
                            TransformableNode transNode = new TransformableNode(arFragment.getTransformationSystem());
                            transNode.setRenderable(renderable);
                            transNode.getScaleController().setMinScale(0.01f);
                            transNode.getScaleController().setMaxScale(2.0f);
                            transNode.setLocalScale(new Vector3(width,0,height));
                            transNode.setLocalPosition(new Vector3(0,0.5f,0));
                            transNode.setLocalRotation(Quaternion.axisAngle(new Vector3(1,0,0),-90));
                            transNode.setParent(anchorNode);
                            transNode.select();
                        });
    }

    public boolean setupAugmentedImagesDb(Config config, Session session) {
        AugmentedImageDatabase augmentedImageDatabase;
        Bitmap bitmap = loadAugmentedImage();
        if (bitmap == null) {
            return false;
        }
        augmentedImageDatabase = new AugmentedImageDatabase(session);
        augmentedImageDatabase.addImage("tiger", bitmap);
        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    private Bitmap loadAugmentedImage() {
        try (InputStream is = getAssets().open("markerpainting.jpg")) {
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            Log.e("ImageLoad", "IO Exception", e);
        }
        return null;
    }

    private String generateFilename() {
        String date =
                new SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault()).format(new Date());
        return Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES) + File.separator + "Sceneform/" + date + "_screenshot.jpg";
    }

    private void saveBitmapToDisk(Bitmap bitmap, String filename) throws IOException {

        File out = new File(filename);
        if (!out.getParentFile().exists()) {
            out.getParentFile().mkdirs();
        }
        try (FileOutputStream outputStream = new FileOutputStream(filename);
             ByteArrayOutputStream outputData = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputData);
            outputData.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException ex) {
            throw new IOException("Failed to save bitmap to disk", ex);
        }
    }

    private void takePhoto() {
        final String filename = generateFilename();
        ArSceneView view = arFragment.getArSceneView();

        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);

        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();

        PixelCopy.request(view, bitmap, (copyResult) -> {
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    saveBitmapToDisk(bitmap, filename);
                } catch (IOException e) {
                    Toast toast = Toast.makeText(MainActivity.this, e.toString(),
                            Toast.LENGTH_LONG);
                    toast.show();
                    return;
                }
                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                        "Photo saved", Snackbar.LENGTH_LONG);
                snackbar.setAction("Open in Photos", v -> {
                    File photoFile = new File(filename);

                    Uri photoURI = FileProvider.getUriForFile(MainActivity.this,
                            MainActivity.this.getPackageName() + ".olczyk.android.justarpaintingmarkerwithphoto",
                            photoFile);
                    Intent intent = new Intent(Intent.ACTION_VIEW, photoURI);
                    intent.setDataAndType(photoURI, "image/*");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);

                });
                snackbar.show();
            } else {
                Toast toast = Toast.makeText(MainActivity.this,
                        "Failed to copyPixels: " + copyResult, Toast.LENGTH_LONG);
                toast.show();
            }
            handlerThread.quitSafely();
        }, new Handler(handlerThread.getLooper()));
        Toast.makeText(this, "Photo taken!", Toast.LENGTH_SHORT).show();
    }
}
